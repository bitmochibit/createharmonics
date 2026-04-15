package me.mochibit.createharmonics.audio.process

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.mochibit.createharmonics.audio.bin.FFMPEGProvider
import me.mochibit.createharmonics.audio.bin.YTDLProvider
import me.mochibit.createharmonics.audio.stream.ProcessBoundInputStream
import me.mochibit.createharmonics.config.ModConfigs
import me.mochibit.createharmonics.foundation.async.modLaunch
import me.mochibit.createharmonics.foundation.err
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.SequenceInputStream
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class FFmpegExecutor private constructor() {
    companion object {
        /**
         * Use a managed FFMPEG process for creating an [InputStream] bound to a url
         */
        suspend fun makeStream(
            url: String,
            sampleRate: Int = 44100,
            seekSeconds: Double = 0.0,
            headers: Map<String, String> = emptyMap(),
            isLive: Boolean = false,
        ): InputStream? {
            val executor = FFmpegExecutor()
            val ready = executor.createStream(url, sampleRate, seekSeconds, headers, isLive)
            if (!ready) {
                executor.destroy()
                return null
            }

            val currentPid =
                executor.processId ?: return null.also {
                    "Null PID process detected, aborting..".err()
                    executor.destroy()
                }

            val currentStream =
                executor.inputStream ?: return null.also {
                    "Null stream detected, aborting...".err()
                    executor.destroy()
                }

            return ProcessBoundInputStream(currentPid, currentStream)
        }
    }

    @Volatile private var process: Process? = null

    @Volatile private var processId: Long? = null
    private val lifecycleMutex = Mutex()

    private val streamReady = CompletableDeferred<Result<Unit>>()

    val inputStream: InputStream?
        get() = process?.inputStream

    fun getSeekString(seconds: Double): String {
        val totalMilliseconds = (seconds * 1000).toLong()

        val hours = totalMilliseconds / 3600000
        val minutes = (totalMilliseconds % 3600000) / 60000
        val secs = (totalMilliseconds % 60000) / 1000
        val millis = totalMilliseconds % 1000

        return String.format("%d:%02d:%02d.%03d", hours, minutes, secs, millis)
    }

    fun isRunning(): Boolean = process?.isAlive == true

    /**
     * Creates an FFmpeg stream and suspends until the stream is ready or timeout occurs.
     *
     * @return true if stream was successfully created and is ready, false otherwise
     */
    suspend fun createStream(
        url: String,
        sampleRate: Int = 44100,
        seekSeconds: Double = 0.0,
        headers: Map<String, String> = emptyMap(),
        isLive: Boolean = false,
    ): Boolean {
        if (!FFMPEGProvider.isAvailable()) {
            "FFmpeg is not available".err()
            return false
        }

        val command =
            buildList {
                add(FFMPEGProvider.getExecutablePath())

                // Add seek offset before input for faster seeking
                if (seekSeconds > 0.0 && !isLive) {
                    add("-ss")
                    add(getSeekString(seekSeconds))
                }

                if (headers.isNotEmpty()) {
                    val headerString =
                        headers.entries
                            .joinToString("\r\n") { (key, value) -> "$key: $value" }
                            .plus(
                                "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/93.0.4577.63 Safari/537.36",
                            ).plus("\r\n")
                    add("-headers")
                    add(headerString)
                }

                add("-reconnect_on_network_error")
                add("1")
                add("-reconnect_on_http_error")
                add("5xx,4xx")

                if (isLive) {
                    add("-reconnect_streamed")
                    add("1")

                    add("-protocol_whitelist")
                    add("file,http,https,tcp,tls,crypto,hls")
                } else {
                    add("-reconnect")
                    add("1")
                    add("-reconnect_delay_max")
                    add("30")
                }

                add("-i")
                add(url)
                add("-f")
                add("s16le")
                add("-ar")
                add(sampleRate.toString())
                add("-ac")
                add("1")
                add("-loglevel")
                add("fatal")

                if (isLive) {
                    add("-max_muxing_queue_size")
                    add((512 * ModConfigs.client.maxPitch.get()).toString())
                }

                add("pipe:1")
            }

        // Guard the entire start sequence so that isRunning() check + assignment are atomic
        val newProcess =
            lifecycleMutex.withLock {
                if (isRunning()) return false

                try {
                    withContext(Dispatchers.IO) {
                        ProcessBuilder(command)
                            .redirectErrorStream(false)
                            .start()
                    }
                } catch (e: Exception) {
                    "Failed to start FFmpeg process: ${e.message}".err()
                    streamReady.complete(Result.failure(e))
                    return false
                }.also { p ->
                    process = p
                    processId = ProcessLifecycleManager.registerProcess(p)
                }
            }

        // Drain stderr so the process doesn't block on a full pipe buffer
        modLaunch(Dispatchers.IO) {
            newProcess.errorStream.bufferedReader().use { reader ->
                try {
                    reader.forEachLine { line -> "FFmpeg stderr: $line".err() }
                } catch (_: Exception) {
                }
            }
        }

        val streamReadyTimeoutMs = 3.minutes.inWholeMilliseconds
        val pollIntervalMs = 1.seconds.inWholeMilliseconds
        val maxAttempts = (streamReadyTimeoutMs / pollIntervalMs).toInt()

        // Monitor stream readiness
        modLaunch(Dispatchers.IO) {
            try {
                var attempts = 0
                while (attempts < maxAttempts && newProcess.isAlive) {
                    try {
                        if (newProcess.inputStream.available() > 0) {
                            streamReady.complete(Result.success(Unit))
                            return@modLaunch
                        }
                    } catch (_: Exception) {
                        // Stream not ready yet
                    }
                    delay(pollIntervalMs)
                    attempts++
                }

                if (!newProcess.isAlive) {
                    streamReady.complete(Result.failure(Exception("FFmpeg process terminated before stream was ready")))
                } else {
                    streamReady.complete(Result.failure(Exception("Stream ready timeout")))
                }
            } catch (e: Exception) {
                streamReady.complete(Result.failure(e))
            }
        }

        return withTimeoutOrNull(streamReadyTimeoutMs) {
            streamReady.await().isSuccess
        } ?: false.also {
            "Timeout waiting for FFmpeg stream to be ready".err()
        }
    }

    /**
     * Destroys the FFmpeg process.
     * Fields are cleared immediately under the mutex; blocking teardown runs outside it.
     */
    suspend fun destroy() {
        val (currentProcess, currentPid) =
            lifecycleMutex.withLock {
                val p = process
                val id = processId
                process = null
                processId = null
                p to id
            }

        currentProcess ?: return

        withContext(Dispatchers.IO) {
            try {
                try {
                    currentProcess.inputStream?.close()
                } catch (_: Exception) {
                }
                try {
                    currentProcess.errorStream?.close()
                } catch (_: Exception) {
                }

                if (currentProcess.isAlive) {
                    currentProcess.destroy()
                    if (!currentProcess.waitFor(100, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                        currentProcess.destroyForcibly()
                        currentProcess.waitFor(50, java.util.concurrent.TimeUnit.MILLISECONDS)
                    }
                }
            } catch (e: Exception) {
                "Error destroying FFmpeg process: ${e.message}".err()
            }
        }

        currentPid?.let { ProcessLifecycleManager.unregisterProcess(it) }
    }
}
