package me.mochibit.createharmonics.audio.process

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.mochibit.createharmonics.audio.bin.FFMPEGProvider
import me.mochibit.createharmonics.audio.stream.ProcessBoundInputStream
import me.mochibit.createharmonics.foundation.async.modLaunch
import me.mochibit.createharmonics.foundation.err
import java.io.InputStream

class FFmpegExecutor private constructor() {
    companion object {
        private const val CHUNK_SIZE = 8192

        /** Base timeout when no seeking is needed. */
        private const val STREAM_READY_BASE_TIMEOUT_MS = 300_000L

        /**
         * Extra milliseconds added per second of seek offset.
         * For large offsets (e.g. 390 s into an 11-hour file) FFmpeg needs
         * more time to fast-seek before it can start writing PCM output.
         * 50 ms/s means a 390-second seek gets ~29 s extra on top of the base.
         */
        private const val STREAM_READY_EXTRA_MS_PER_SEEK_SECOND = 50L

        /** Hard ceiling so we never wait forever. */
        private const val STREAM_READY_MAX_TIMEOUT_MS = 400_000L

        /**
         * Use a managed FFMPEG process for creating an [InputStream] bound to a url
         */
        suspend fun makeStream(
            url: String,
            sampleRate: Int = 44100,
            seekSeconds: Double = 0.0,
            headers: Map<String, String> = emptyMap(),
        ): InputStream? {
            val executor = FFmpegExecutor()
            executor.createStream(url, sampleRate, seekSeconds, headers)
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

    val currentProcessId get() = processId

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
    ): Boolean {
        if (!FFMPEGProvider.isAvailable()) {
            "FFmpeg is not available".err()
            return false
        }

        val command =
            buildList {
                add(FFMPEGProvider.getExecutablePath())

                // Add seek offset before input for faster seeking
                if (seekSeconds > 0.0) {
                    add("-ss")
                    add(getSeekString(seekSeconds))
                }

                // Protocol whitelist for HLS/HTTPS streams (must be before -i)
                add("-protocol_whitelist")
                add("file,http,https,tcp,tls,crypto,hls,applehttp")

                // Add user agent for better compatibility
                add("-user_agent")
                add("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

                if (headers.isNotEmpty()) {
                    val headerString =
                        headers.entries
                            .joinToString("\r\n") { (key, value) -> "$key: $value" }
                            .plus("\r\n")
                    add("-headers")
                    add(headerString)
                }

                add("-reconnect")
                add("1")
                add("-reconnect_streamed")
                add("1")
                add("-reconnect_delay_max")
                add("5")
                add("-multiple_requests")
                add("1")
                add("-timeout")
                add("10000000")
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

        // Monitor process lifecycle — clear refs under the mutex to avoid racing with destroy()
        modLaunch(Dispatchers.IO) {
            try {
                newProcess.waitFor()
            } catch (_: Exception) {
            }
            lifecycleMutex.withLock {
                if (process == newProcess) {
                    process = null
                    processId = null
                }
            }
        }

        // Dynamic timeout: base + extra per seek-second, capped at the hard ceiling
        val streamReadyTimeoutMs =
            minOf(
                STREAM_READY_BASE_TIMEOUT_MS + (seekSeconds * STREAM_READY_EXTRA_MS_PER_SEEK_SECOND).toLong(),
                STREAM_READY_MAX_TIMEOUT_MS,
            )
        val pollIntervalMs = 100L
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
