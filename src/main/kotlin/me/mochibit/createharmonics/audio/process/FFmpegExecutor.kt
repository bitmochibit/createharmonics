package me.mochibit.createharmonics.audio.process

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.audio.binProvider.FFMPEGProvider
import me.mochibit.createharmonics.coroutine.launchModCoroutine
import java.io.InputStream

class FFmpegExecutor {
    companion object {
        private const val CHUNK_SIZE = 8192
        private const val STREAM_READY_TIMEOUT_MS = 5000L
    }

    private var process: Process? = null
    private var processId: Long? = null

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

    /**
     * Creates an FFmpeg stream and suspends until the stream is ready or timeout occurs.

     * @return true if stream was successfully created and is ready, false otherwise
     */
    suspend fun createStream(
        url: String,
        sampleRate: Int = 48000,
        seekSeconds: Double = 0.0,
    ): Boolean {
        if (!FFMPEGProvider.isAvailable()) {
            Logger.err("FFmpeg is not available")
            return false
        }
        if (isRunning()) {
            Logger.err("FFmpeg process is already running")
            return false
        }

        val ffmpegPath = FFMPEGProvider.getExecutablePath()

        val command =
            buildList {
                add(ffmpegPath)

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

                // Note: HTTP headers are intentionally not passed to FFmpeg as they cause
                // formatting issues. yt-dlp already uses them to extract the URL.

                // Network resilience parameters - aggressive timeouts to prevent hanging
                add("-reconnect")
                add("1")
                add("-reconnect_streamed")
                add("1")
                add("-reconnect_delay_max")
                add("2")
                add("-i")
                add(url)
                add("-f")
                add("s16le")
                add("-ar")
                add(sampleRate.toString())
                add("-ac")
                add("1")
                add("-loglevel")
                add("warning")
                add("pipe:1")
            }

        val newProcess =
            try {
                withContext(Dispatchers.IO) {
                    ProcessBuilder(command)
                        .redirectErrorStream(false)
                        .start()
                }
            } catch (e: Exception) {
                Logger.err("Failed to start FFmpeg process: ${e.message}")
                streamReady.complete(Result.failure(e))
                return false
            }

        process = newProcess
        processId = ProcessLifecycleManager.registerProcess(newProcess)

        // Monitor process lifecycle
        launchModCoroutine(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val exitCode = newProcess.waitFor()
            val runtime = System.currentTimeMillis() - startTime

            if (exitCode != 0) {
                Logger.err("FFmpeg process exited with code $exitCode after ${runtime}ms")
            } else {
                Logger.info("FFmpeg process completed successfully after ${runtime}ms")
            }

            // Clean up when process exits naturally
            if (process == newProcess) {
                processId?.let { ProcessLifecycleManager.destroyProcess(it) }
                process = null
                processId = null
            }
        }

        // Monitor stream readiness
        launchModCoroutine(Dispatchers.IO) {
            try {
                // Wait for stream to have data available
                var attempts = 0
                val maxAttempts = 50 // 50 * 100ms = 5 seconds
                while (attempts < maxAttempts && newProcess.isAlive) {
                    try {
                        val available = newProcess.inputStream.available()
                        if (available > 0) {
                            streamReady.complete(Result.success(Unit))
                            return@launchModCoroutine
                        }
                    } catch (_: Exception) {
                        // Stream not ready yet
                    }
                    delay(100)
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

        return withTimeoutOrNull(STREAM_READY_TIMEOUT_MS) {
            val result = streamReady.await()
            result.isSuccess
        } ?: false.also {
            Logger.err("Timeout waiting for FFmpeg stream to be ready")
        }
    }

    fun isRunning(): Boolean = process?.isAlive == true

    /**
     * Destroys the FFmpeg process asynchronously.
     * This method returns immediately and performs cleanup in the background.
     */
    fun destroy() {
        val currentProcess = process
        val currentProcessId = processId

        // Clear references immediately
        process = null
        processId = null

        // Perform actual cleanup asynchronously to avoid blocking
        if (currentProcess != null) {
            launchModCoroutine(Dispatchers.IO) {
                try {
                    // Close streams first to signal shutdown
                    try {
                        currentProcess.inputStream?.close()
                    } catch (_: Exception) {
                        // Already closed or error, continue
                    }

                    try {
                        currentProcess.errorStream?.close()
                    } catch (_: Exception) {
                        // Already closed or error, continue
                    }

                    // Attempt graceful termination if still alive
                    if (currentProcess.isAlive) {
                        currentProcess.destroy()

                        // Wait briefly for graceful shutdown (100ms)
                        if (!currentProcess.waitFor(100, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                            // Still alive, force kill
                            currentProcess.destroyForcibly()
                            // Wait a bit for force kill
                            currentProcess.waitFor(50, java.util.concurrent.TimeUnit.MILLISECONDS)
                        }
                    }

                    Logger.info("FFmpeg process ${currentProcessId ?: "unknown"} terminated")
                } catch (e: Exception) {
                    Logger.err("Error destroying FFmpeg process: ${e.message}")
                }

                // Unregister from ProcessLifecycleManager
                currentProcessId?.let { id ->
                    // Just remove from tracking (process is already terminated above)
                    ProcessLifecycleManager.unregisterProcess(id)
                }
            }
        }
    }
}
