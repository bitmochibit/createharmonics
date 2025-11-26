package me.mochibit.createharmonics.audio.process

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.mochibit.createharmonics.Logger.err
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.audio.binProvider.FFMPEGProvider
import java.io.File

/**
 * Wrapper for FFmpeg operations with managed process lifecycle.
 * Provides high-level operations like streaming, downloading, and decoding.
 */
class FFmpegExecutor {
    companion object {
        private const val CHUNK_SIZE = 8192
    }

    /**
     * Result of an FFmpeg operation.
     */
    sealed class FFmpegResult {
        data class Success(val bytesProcessed: Long) : FFmpegResult()
        data class Error(val exitCode: Int, val message: String) : FFmpegResult()
    }

    /**
     * Download audio from URL to file using FFmpeg.
     */
    suspend fun downloadAudio(audioUrl: String, outputFile: File): FFmpegResult = withContext(Dispatchers.IO) {
        val ffmpegPath = FFMPEGProvider.getExecutablePath()
            ?: return@withContext FFmpegResult.Error(-1, "FFmpeg not found")

        // Delete partial downloads if they exist
        if (outputFile.exists()) {
            outputFile.delete()
        }

        val command = listOf(
            ffmpegPath,
            "-reconnect", "1",
            "-reconnect_streamed", "1",
            "-reconnect_delay_max", "5",
            "-i", audioUrl,
            "-vn", // No video
            "-c:a", "copy", // Copy audio stream without re-encoding
            "-y", // Overwrite output file without asking
            "-loglevel", "warning",
            outputFile.absolutePath
        )

        info("FFmpeg download command: ${command.joinToString(" ")}")
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val processId = ProcessLifecycleManager.registerProcess(process)

        try {
            // Monitor output asynchronously
            val outputJob = launch {
                process.inputStream.bufferedReader().use { reader ->
                    reader.lineSequence().forEach { line ->
                        if (line.isNotBlank()) {
                            info("FFmpeg: $line")
                        }
                    }
                }
            }

            val exitCode = process.waitFor()
            outputJob.cancel()

            if (exitCode != 0) {
                // Clean up failed download
                if (outputFile.exists()) {
                    outputFile.delete()
                }
                err("FFmpeg download failed with exit code: $exitCode")
                return@withContext FFmpegResult.Error(exitCode, "FFmpeg download failed")
            }

            // Validate the downloaded file
            if (!outputFile.exists() || outputFile.length() == 0L) {
                err("Downloaded file is missing or empty")
                return@withContext FFmpegResult.Error(-1, "Downloaded file validation failed")
            }

            info("FFmpeg download completed successfully: ${outputFile.length()} bytes")
            FFmpegResult.Success(outputFile.length())
        } finally {
            ProcessLifecycleManager.unregisterProcess(processId)
            if (process.isAlive) {
                process.destroy()
            }
        }
    }

    /**
     * Decode audio URL directly to PCM stream.
     */
    fun decodeUrlToStream(audioUrl: String, sampleRate: Int): Flow<ByteArray> = flow {
        val ffmpegPath = FFMPEGProvider.getExecutablePath()
            ?: throw IllegalStateException("FFmpeg not found")

        val command = listOf(
            ffmpegPath,
            "-reconnect", "1",
            "-reconnect_streamed", "1",
            "-reconnect_delay_max", "5",
            "-i", audioUrl,
            "-f", "s16le",
            "-ar", sampleRate.toString(),
            "-ac", "1",
            "-loglevel", "warning",
            "pipe:1"
        )

        info("Starting FFmpeg stream: ${command.joinToString(" ")}")
        val process = ProcessBuilder(command)
            .redirectErrorStream(false)
            .start()

        val processId = ProcessLifecycleManager.registerProcess(process)

        try {
            // Monitor stderr for errors
            val errorJob = CoroutineScope(Dispatchers.IO).launch {
                process.errorStream.bufferedReader().use { reader ->
                    reader.lineSequence().forEach { line ->
                        if (line.isNotBlank()) {
                            err("FFmpeg: $line")
                        }
                    }
                }
            }

            try {
                val buffer = ByteArray(CHUNK_SIZE)
                var totalBytes = 0L

                process.inputStream.use { input ->
                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) break

                        emit(buffer.copyOf(bytesRead))
                        totalBytes += bytesRead
                    }
                }

                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    err("FFmpeg process exited with code: $exitCode")
                }
                info("Decoded $totalBytes bytes of PCM")
            } finally {
                errorJob.cancel()
            }
        } finally {
            ProcessLifecycleManager.unregisterProcess(processId)
            if (process.isAlive) {
                process.destroy()
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Decode audio file directly to PCM stream.
     */
    fun decodeFileToStream(audioFile: File, sampleRate: Int): Flow<ByteArray> = flow {
        val ffmpegPath = FFMPEGProvider.getExecutablePath()
            ?: throw IllegalStateException("FFmpeg not found")

        val command = listOf(
            ffmpegPath,
            "-i", audioFile.absolutePath,
            "-f", "s16le",
            "-ar", sampleRate.toString(),
            "-ac", "1",
            "-loglevel", "error",
            "pipe:1"
        )

        val process = ProcessBuilder(command).start()
        val processId = ProcessLifecycleManager.registerProcess(process)

        try {
            val buffer = ByteArray(CHUNK_SIZE)
            var totalBytes = 0L

            process.inputStream.use { input ->
                while (true) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) break

                    emit(buffer.copyOf(bytesRead))
                    totalBytes += bytesRead
                }
            }

            info("Decoded $totalBytes bytes of PCM from file")
        } finally {
            ProcessLifecycleManager.unregisterProcess(processId)
            if (process.isAlive) {
                process.destroy()
            }
        }
    }.flowOn(Dispatchers.IO)
}

