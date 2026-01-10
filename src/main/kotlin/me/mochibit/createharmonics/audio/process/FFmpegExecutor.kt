package me.mochibit.createharmonics.audio.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
    private var streamReadyChannel: Channel<Result<Unit>>? = null

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

                add("-reconnect")
                add("1")
                add("-reconnect_streamed")
                add("1")
                add("-reconnect_delay_max")
                add("5")
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

        // Create channel for stream ready signal
        val channel = Channel<Result<Unit>>(capacity = 1)
        streamReadyChannel = channel

        val newProcess =
            try {
                ProcessBuilder(command)
                    .redirectErrorStream(false)
                    .start()
            } catch (e: Exception) {
                Logger.err("Failed to start FFmpeg process: ${e.message}")
                channel.trySend(Result.failure(e))
                return false
            }

        process = newProcess
        processId = ProcessLifecycleManager.registerProcess(newProcess)

        // Monitor error stream for debugging
        launchModCoroutine(Dispatchers.IO) {
            try {
                newProcess.errorStream.bufferedReader().use { reader ->
                    reader.lineSequence().forEach { line ->
                        if (line.isNotBlank()) {
                            Logger.err("FFmpeg: $line")
                        }
                    }
                }
            } catch (e: Exception) {
                // Stream closed, that's fine
            }
        }

        // Monitor process lifecycle
        launchModCoroutine(Dispatchers.IO) {
            val exitCode = newProcess.waitFor()
            if (exitCode != 0) {
                Logger.err("FFmpeg process exited with code $exitCode")
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
                            channel.trySend(Result.success(Unit))
                            return@launchModCoroutine
                        }
                    } catch (_: Exception) {
                        // Stream not ready yet
                    }
                    delay(100)
                    attempts++
                }

                // Timeout or process died
                if (!newProcess.isAlive) {
                    channel.trySend(Result.failure(Exception("FFmpeg process terminated unexpectedly")))
                } else {
                    channel.trySend(Result.failure(Exception("Stream ready timeout")))
                }
            } catch (e: Exception) {
                channel.trySend(Result.failure(e))
            }
        }

        // Wait for stream to be ready with timeout
        return withTimeoutOrNull(STREAM_READY_TIMEOUT_MS) {
            val result = channel.receive()
            result.isSuccess
        } ?: false.also {
            Logger.err("Timeout waiting for FFmpeg stream to be ready")
        }
    }

    fun isRunning(): Boolean = process?.isAlive == true

    fun destroy() {
        streamReadyChannel?.close()
        streamReadyChannel = null
        processId?.let {
            ProcessLifecycleManager.destroyProcess(it)
            processId = null
        }
        process = null
    }
}
