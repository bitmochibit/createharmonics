package me.mochibit.createharmonics.audio.upload

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.time.Instant
import java.util.Properties
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.outputStream

data class AudioMetadata(
    val fileId: String,
    val playerName: String,
    val extension: String,
    val originalFileName: String?,
    val title: String?,
    val artist: String?,
    val sizeBytes: Long,
    val uploadedAt: Instant,
)

class QuotaExceededException(message: String) : RuntimeException(message)
class FileTooLargeException(message: String) : RuntimeException(message)

class AudioStorageManager(private val config: AudioUploadConfig) {

    private fun playerDir(playerName: String): Path =
        InputSanitizer.resolveContained(config.storageRoot, playerName)

    fun countFiles(playerName: String): Int {
        val dir = playerDir(playerName)
        if (!dir.exists()) return 0
        Files.newDirectoryStream(dir, "*.properties").use { stream ->
            return stream.count()
        }
    }

    /**
     * Streams [body] into a new file for [playerName], enforcing the
     * per-file size limit *while copying* (never trusting Content-Length
     * alone) and the per-player file count quota up front. Any partial
     * file is removed on failure.
     */
    fun store(
        playerName: String,
        extension: String,
        originalFileName: String?,
        title: String?,
        artist: String?,
        body: InputStream,
    ): AudioMetadata {
        playerDir(playerName) // validates playerName before touching disk
        val dir = Files.createDirectories(playerDir(playerName))

        if (countFiles(playerName) >= config.maxFilesPerPlayer) {
            throw QuotaExceededException(
                "Player '$playerName' already has the maximum of ${config.maxFilesPerPlayer} audio files"
            )
        }

        val fileId = InputSanitizer.newFileId()
        val target = InputSanitizer.resolveContained(config.storageRoot, playerName, "$fileId.$extension")
        val metaTarget = InputSanitizer.resolveContained(config.storageRoot, playerName, "$fileId.properties")

        val sizeBytes = try {
            target.outputStream().use { out -> copyBounded(body, out, config.maxFileSizeBytes) }
        } catch (e: FileTooLargeException) {
            target.deleteIfExists()
            throw e
        } catch (e: IOException) {
            target.deleteIfExists()
            throw e
        }

        val metadata = AudioMetadata(
            fileId = fileId,
            playerName = playerName,
            extension = extension,
            originalFileName = originalFileName,
            title = title,
            artist = artist,
            sizeBytes = sizeBytes,
            uploadedAt = Instant.now(),
        )
        writeMetadata(metaTarget, metadata)
        return metadata
    }

    fun resolveAudioFile(playerName: String, fileId: String, extension: String): Path {
        val target = InputSanitizer.resolveContained(config.storageRoot, playerName, "$fileId.$extension")
        if (!target.exists() || !Files.isRegularFile(target)) {
            throw NoSuchFileException(target.toString())
        }
        return target
    }

    private fun writeMetadata(path: Path, meta: AudioMetadata) {
        val props = Properties()
        props.setProperty("fileId", meta.fileId)
        props.setProperty("playerName", meta.playerName)
        props.setProperty("extension", meta.extension)
        meta.originalFileName?.let { props.setProperty("originalFileName", it) }
        meta.title?.let { props.setProperty("title", it) }
        meta.artist?.let { props.setProperty("artist", it) }
        props.setProperty("sizeBytes", meta.sizeBytes.toString())
        props.setProperty("uploadedAt", meta.uploadedAt.toString())
        path.outputStream().use { props.store(it, "CreateHarmonics audio metadata") }
    }

    private fun copyBounded(input: InputStream, output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(8 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            if (total > limit) {
                throw FileTooLargeException("Upload exceeds the maximum allowed size of $limit bytes")
            }
            output.write(buffer, 0, read)
        }
        return total
    }
}