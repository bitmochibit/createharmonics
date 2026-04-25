package me.mochibit.createharmonics.audio.process

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.mochibit.createharmonics.audio.bin.FFMPEGProvider
import me.mochibit.createharmonics.audio.stream.ProcessBoundInputStream
import me.mochibit.createharmonics.config.ModConfigs
import me.mochibit.createharmonics.foundation.async.modLaunch
import me.mochibit.createharmonics.foundation.err
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.SequenceInputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class FFmpegExecutor private constructor() {
    companion object {
        /** Minimum timeout (minutes) regardless of seek position. */
        private const val BASE_TIMEOUT_MINUTES = 2L

        /** Extra minutes added per hour of seek offset, to account for DASH segment navigation. */
        private const val EXTRA_TIMEOUT_PER_SEEK_HOUR = 2L

        /**
         * Use a managed FFmpeg process for creating an [InputStream] bound to a url.
         *
         * The returned stream is a [SequenceInputStream] that prepends the first
         * chunk already consumed during readiness probing, so no audio data is lost.
         */
        suspend fun makeStream(
            url: String,
            sampleRate: Int = 44100,
            seekSeconds: Double = 0.0,
            headers: Map<String, String> = emptyMap(),
            isLive: Boolean = false,
        ): Pair<InputStream, FFmpegExecutor>? {
            val executor = FFmpegExecutor()
            val firstChunk = executor.createStream(url, sampleRate, seekSeconds, headers, isLive)
            if (firstChunk == null) {
                executor.destroy()
                return null
            }

            val currentPid =
                executor.processId ?: run {
                    "Null PID process detected, aborting..".err()
                    executor.destroy()
                    return null
                }

            val currentStream =
                executor.inputStream ?: run {
                    "Null stream detected, aborting...".err()
                    executor.destroy()
                    return null
                }

            val fullStream = SequenceInputStream(ByteArrayInputStream(firstChunk), currentStream)
            return ProcessBoundInputStream(currentPid, fullStream) to executor
        }

        private fun getTimeout(isLive: Boolean): Duration {
            var base = 10.seconds
            if (isLive) {
                base += 2.minutes
            }
            return base
        }
    }

    @Volatile private var process: Process? = null

    @Volatile private var processId: Long? = null

    private val lifecycleMutex = Mutex()

    val inputStream: InputStream?
        get() = process?.inputStream

    fun isRunning(): Boolean = process?.isAlive == true

    fun getSeekString(seconds: Double): String {
        val totalMilliseconds = (seconds * 1000).toLong()
        val hours = totalMilliseconds / 3600000
        val minutes = (totalMilliseconds % 3600000) / 60000
        val secs = (totalMilliseconds % 60000) / 1000
        val millis = totalMilliseconds % 1000
        return String.format("%d:%02d:%02d.%03d", hours, minutes, secs, millis)
    }

    /**
     * Starts the FFmpeg process and suspends until the first bytes of audio arrive
     * (blocking read, no polling) or the dynamic timeout elapses.
     *
     * @return The first chunk of raw PCM bytes if the stream started successfully, null otherwise.
     */
    suspend fun createStream(
        url: String,
        sampleRate: Int = 44100,
        seekSeconds: Double = 0.0,
        headers: Map<String, String> = emptyMap(),
        isLive: Boolean = false,
    ): ByteArray? {
        if (!FFMPEGProvider.isAvailable()) {
            "FFmpeg is not available".err()
            return null
        }

        val command =
            buildList {
                add(FFMPEGProvider.getExecutablePath())

                if (seekSeconds > 0.0 && !isLive) {
                    add("-ss")
                    add(getSeekString(seekSeconds))
                }

                if (headers.isNotEmpty()) {
                    val headerString =
                        headers.entries
                            .joinToString("\r\n") { (key, value) -> "$key: $value" }
                            .plus(
                                "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/93.0.4577.63 Safari/537.36",
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

        val newProcess =
            lifecycleMutex.withLock {
                if (isRunning()) return null
                try {
                    withContext(Dispatchers.IO) {
                        ProcessBuilder(command).redirectErrorStream(false).start()
                    }
                } catch (e: Exception) {
                    "Failed to start FFmpeg process: ${e.message}".err()
                    return null
                }.also { p ->
                    process = p
                    processId = ProcessLifecycleManager.registerProcess(p)
                }
            }

        modLaunch(Dispatchers.IO) {
            newProcess.errorStream.bufferedReader().use { reader ->
                try {
                    reader.forEachLine { line -> "FFmpeg stderr: $line".err() }
                } catch (_: Exception) {
                }
            }
        }

        val timeoutMs = getTimeout(isLive)

        val firstChunkDeferred = CompletableDeferred<ByteArray?>()
        val readJob =
            modLaunch(Dispatchers.IO) {
                try {
                    val buf = ByteArray(4096)
                    val n = newProcess.inputStream.read(buf)
                    firstChunkDeferred.complete(if (n > 0) buf.copyOf(n) else null)
                } catch (_: Exception) {
                    firstChunkDeferred.complete(null)
                }
            }

        return try {
            val chunk = withTimeoutOrNull(timeoutMs) { firstChunkDeferred.await() }
            if (chunk == null) {
                destroy()
                readJob.join()
            }
            chunk
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                destroy()
                readJob.cancelAndJoin()
            }
            throw e
        }
    }

    /**
     * Destroys the FFmpeg process.
     * Fields are cleared immediately under the mutex; blocking teardown runs on IO dispatcher.
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
                if (currentProcess.isAlive) {
                    currentProcess.destroy()
                    if (!currentProcess.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                        currentProcess.destroyForcibly()
                        currentProcess.waitFor(50, java.util.concurrent.TimeUnit.MILLISECONDS)
                    }
                }

                try {
                    currentProcess.inputStream?.close()
                } catch (_: Exception) {
                }
                try {
                    currentProcess.errorStream?.close()
                } catch (_: Exception) {
                }
            } catch (e: Exception) {
                "Error destroying FFmpeg process: ${e.message}".err()
            }
        }

        currentPid?.let { ProcessLifecycleManager.unregisterProcess(it) }
    }
}
