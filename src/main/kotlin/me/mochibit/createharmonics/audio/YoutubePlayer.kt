package me.mochibit.createharmonics.audio

import kotlinx.coroutines.*
import me.mochibit.createharmonics.Logger.err
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.audio.provider.FFMPEG
import me.mochibit.createharmonics.audio.provider.YTDL
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.TimeUnit

object YoutubePlayer {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Streams audio from a YouTube URL using yt-dlp and FFmpeg.
     * Returns immediately with an InputStream that will be populated asynchronously.
     * This method is Java-compatible and doesn't require coroutine context.
     *
     * @param url The YouTube URL to stream from
     * @param format The audio format to output (default: "wav", can be "mp3", "opus", etc.)
     * @param sampleRate The audio sample rate (default: 48000)
     * @param channels Number of audio channels (default: 2 for stereo)
     * @param bufferSize The size of the pipe buffer (default: 64KB)
     * @return An InputStream that provides the audio data as it becomes available
     */
    fun streamAudio(
        url: String,
        format: String = "ogg",
        sampleRate: Int = 48000,
        channels: Int = 2,
        bufferSize: Int = 65536
    ): InputStream {
        val pipedInputStream = PipedInputStream(bufferSize)
        val pipedOutputStream = PipedOutputStream(pipedInputStream)

        // Start the streaming process asynchronously
        scope.launch {
            try {
                streamAudioAsync(url, format, sampleRate, channels, pipedOutputStream)
            } catch (e: Exception) {
                if (e !is java.io.IOException || e.message != "Pipe closed") {
                    err("Error in async streaming: ${e.message}")
                    e.printStackTrace()
                } else {
                    info("Stream closed by consumer")
                }
            } finally {
                try {
                    pipedOutputStream.close()
                } catch (e: Exception) {
                    // Ignore close errors
                }
            }
        }

        return pipedInputStream
    }

    /**
     * Internal coroutine function that performs the actual streaming.
     */
    private suspend fun streamAudioAsync(
        url: String,
        format: String,
        sampleRate: Int,
        channels: Int,
        outputStream: PipedOutputStream
    ) = withContext(Dispatchers.IO) {
        try {
            // Ensure providers are installed
            if (!ensureProvidersInstalled()) {
                err("Failed to ensure audio providers are installed")
                return@withContext
            }

            // Get audio URL from yt-dlp
            val audioUrl = getAudioUrl(url) ?: run {
                err("Failed to get audio URL from: $url")
                return@withContext
            }

            info("Got audio URL, starting FFmpeg stream...")

            // Stream audio through FFmpeg
            streamWithFFmpeg(audioUrl, format, sampleRate, channels, outputStream)
        } catch (e: Exception) {
            err("Error streaming audio: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Ensures that both yt-dlp and FFmpeg are installed and available.
     */
    private suspend fun ensureProvidersInstalled(): Boolean = withContext(Dispatchers.IO) {
        val ytdlAvailable = async {
            if (!YTDL.isAvailable()) {
                info("yt-dlp not found, installing...")
                YTDL.install()
            } else {
                true
            }
        }

        val ffmpegAvailable = async {
            if (!FFMPEG.isAvailable()) {
                info("FFmpeg not found, installing...")
                FFMPEG.install()
            } else {
                true
            }
        }

        return@withContext ytdlAvailable.await() && ffmpegAvailable.await()
    }

    /**
     * Uses yt-dlp to extract the direct audio stream URL from a YouTube URL.
     */
    private suspend fun getAudioUrl(youtubeUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val ytdlPath = YTDL.getExecutablePath() ?: run {
                err("yt-dlp executable not found")
                return@withContext null
            }

            info("Extracting audio URL from: $youtubeUrl")

            // Use yt-dlp to get the best audio URL
            val process = ProcessBuilder(
                ytdlPath,
                "-f", "bestaudio",
                "--get-url",
                "--no-playlist",
                youtubeUrl
            ).apply {
                redirectErrorStream(true)
            }.start()

            val output = process.inputStream.bufferedReader().use { it.readText() }

            val exitCode = withTimeoutOrNull(30000) {
                process.waitFor()
            } ?: run {
                process.destroyForcibly()
                err("yt-dlp process timed out")
                return@withContext null
            }

            if (exitCode != 0) {
                err("yt-dlp failed with exit code $exitCode: $output")
                return@withContext null
            }

            val audioUrl = output.trim().lines().firstOrNull { it.startsWith("http") }

            if (audioUrl == null) {
                err("Could not extract audio URL from yt-dlp output")
                return@withContext null
            }

            info("Successfully extracted audio URL")
            return@withContext audioUrl
        } catch (e: Exception) {
            err("Error getting audio URL: ${e.message}")
            e.printStackTrace()
            return@withContext null
        }
    }

    /**
     * Streams audio through FFmpeg, converting it to the desired format and writing to output stream.
     */
    private suspend fun streamWithFFmpeg(
        audioUrl: String,
        format: String,
        sampleRate: Int,
        channels: Int,
        outputStream: PipedOutputStream
    ) = withContext(Dispatchers.IO) {
        var process: Process? = null
        try {
            val ffmpegPath = FFMPEG.getExecutablePath() ?: run {
                err("FFmpeg executable not found")
                return@withContext
            }

            // Build FFmpeg command
            val command = buildList {
                add(ffmpegPath)
                add("-i")
                add(audioUrl)
                add("-f")
                add(format)
                add("-ar")
                add(sampleRate.toString())
                add("-ac")
                add(channels.toString())

                // Additional quality settings
                when (format) {
                    "wav" -> {
                        add("-acodec")
                        add("pcm_s16le")
                    }
                    "mp3" -> {
                        add("-acodec")
                        add("libmp3lame")
                        add("-b:a")
                        add("192k")
                    }
                    "ogg" -> {
                        add("-acodec")
                        add("libvorbis")
                        add("-q:a")
                        add("6") // Quality level 6 (good quality, ~192kbps)
                    }
                    "opus" -> {
                        add("-acodec")
                        add("libopus")
                        add("-b:a")
                        add("128k")
                    }
                }

                // Hide FFmpeg banner and output to stdout
                add("-loglevel")
                add("error")
                add("pipe:1")
            }

            info("Starting FFmpeg process...")

            process = ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start()

            val currentProcess = process

            // Monitor stderr in a separate coroutine
            launch {
                currentProcess.errorStream.bufferedReader().use { reader ->
                    reader.lineSequence().forEach { line ->
                        if (line.isNotBlank()) {
                            err("FFmpeg: $line")
                        }
                    }
                }
            }

            // Copy data from FFmpeg output to the piped output stream
            val buffer = ByteArray(16384) // Increased buffer size
            currentProcess.inputStream.use { input ->
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    try {
                        outputStream.write(buffer, 0, bytesRead)
                        outputStream.flush()
                    } catch (e: java.io.IOException) {
                        if (e.message == "Pipe closed") {
                            info("Consumer closed the stream, stopping FFmpeg")
                            currentProcess.destroyForcibly()
                            return@withContext
                        }
                        throw e
                    }
                }
            }

            info("FFmpeg streaming completed successfully")

        } catch (e: java.io.IOException) {
            if (e.message != "Pipe closed") {
                err("I/O error streaming with FFmpeg: ${e.message}")
                e.printStackTrace()
            }
        } catch (e: Exception) {
            err("Error streaming with FFmpeg: ${e.message}")
            e.printStackTrace()
        } finally {
            process?.let {
                if (it.isAlive) {
                    if (!it.waitFor(2, TimeUnit.SECONDS)) {
                        it.destroyForcibly()
                    }
                }
            }
        }
    }

    /**
     * Cancels all running coroutines in this player.
     * Should be called when shutting down.
     */
    fun shutdown() {
        scope.cancel()
    }
}