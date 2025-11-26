package me.mochibit.createharmonics.audio.processor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import me.mochibit.createharmonics.Logger.err
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.audio.binProvider.FFMPEGProvider
import me.mochibit.createharmonics.audio.process.FFmpegExecutor
import me.mochibit.createharmonics.audio.source.AudioSource
import java.nio.file.Files
import java.nio.file.Paths


class AudioStreamProcessor(
    private val sampleRate: Int = 48000
) {
    companion object {
        private const val DURATION_THRESHOLD_SECONDS = 180 // 3 minutes
        private val DOWNLOAD_DIR = Paths.get("downloaded_audio")
    }

    private val ffmpegWrapper = FFmpegExecutor()

    init {
        if (!FFMPEGProvider.isAvailable()) {
            throw IllegalStateException("FFMPEG binary is not available, check for installation errors")
        }
        Files.createDirectories(DOWNLOAD_DIR)
    }

    fun processAudioStream(
        audioSource: AudioSource
    ): Flow<ByteArray> = flow {
        val identifier = audioSource.getIdentifier()
        val duration = audioSource.getDurationSeconds()

        val shouldDownload = duration <= DURATION_THRESHOLD_SECONDS

        val audioUrl = audioSource.resolveAudioUrl()
        val sourceFlow = if (shouldDownload) {
            processWithDownload(audioUrl, identifier)
        } else {
            processWithStreaming(audioUrl)
        }

        emitAll(sourceFlow)
    }.flowOn(Dispatchers.IO)

    /**
     * Stream audio directly from URL to output.
     */
    private fun processWithStreaming(
        audioUrl: String
    ): Flow<ByteArray> = ffmpegWrapper
        .decodeUrlToStream(audioUrl, sampleRate)
        .onStart { info("Starting FFmpeg decode (streaming)") }
        .onCompletion { info("FFmpeg decode completed") }
        .catch { e ->
            err("Decode error: ${e.message}")
            throw e
        }

    /**
     * Download audio to file first, then process from file.
     */
    private fun processWithDownload(
        audioUrl: String,
        identifier: String
    ): Flow<ByteArray> = flow {
        // Create unique filename with proper audio extension
        val sanitizedName = identifier.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val audioFile = DOWNLOAD_DIR.resolve("${sanitizedName}.opus").toFile()

        try {
            // Download if not already cached
            if (!audioFile.exists()) {
                info("Downloading audio to: ${audioFile.absolutePath}")
                val result = ffmpegWrapper.downloadAudio(audioUrl, audioFile)
                when (result) {
                    is FFmpegExecutor.FFmpegResult.Success -> {
                        info("Download completed: ${result.bytesProcessed} bytes")
                    }
                    is FFmpegExecutor.FFmpegResult.Error -> {
                        err("Download failed: ${result.message}")
                        throw IllegalStateException("Download failed: ${result.message}")
                    }
                }
            } else {
                info("Using cached audio file: ${audioFile.absolutePath}")
            }

            // Process from local file - stream directly
            info("Starting FFmpeg decode from file")
            emitAll(ffmpegWrapper.decodeFileToStream(audioFile, sampleRate))
        } catch (e: Exception) {
            err("Error processing downloaded audio: ${e.message}")
            throw e
        }
    }
}