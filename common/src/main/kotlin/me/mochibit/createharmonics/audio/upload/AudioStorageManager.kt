package me.mochibit.createharmonics.audio.upload

import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.*
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream


object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

const val MAX_DISPLAY_NAME_LENGTH = 64
@Serializable
data class AudioMetadata(
    val fileId: String,
    val extension: String,
    val playerName: String,
    val originalFileName: String?,
    val customName: String? = null,
    val sizeBytes: Long,
    val durationSeconds: Int = 0,
    val sampleRate: Int = 48000,
    @Serializable(with = InstantSerializer::class) val uploadedAt: Instant,
) {
    val displayName
        get() = customName?.ifBlank { null } ?: originalFileName?.ifBlank { null } ?: fileId
}
class QuotaExceededException(message: String) : RuntimeException(message)
class FileTooLargeException(message: String) : RuntimeException(message)

data class AudioStorageConfiguration(
    val storageRoot: Path,
    val maxFilesPerPlayer: Int,
    val maxFileSizeBytes: Long,
)

class AudioStorageManager(private val config: AudioStorageConfiguration) {

    constructor(
        storageRoot: Path,
        maxFilesPerPlayer: Int = 10,
        maxFileSizeBytes: Long = 10 * 1024 * 1024L
    ) : this(
        AudioStorageConfiguration(storageRoot, maxFilesPerPlayer, maxFileSizeBytes)
    )

    fun currentConfig(): AudioStorageConfiguration = this.config.copy()

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
        originalFileName: String?,
        extension: String,
        durationSeconds: Int,
        sampleRate: Int,
        body: InputStream,
        onProgress: (Long) -> Unit,
        beforeCommit: () -> Unit = {},
    ): AudioMetadata {
        Files.createDirectories(playerDir(playerName))

        if (countFiles(playerName) >= config.maxFilesPerPlayer) {
            throw QuotaExceededException(
                "Player '$playerName' already has the maximum of ${config.maxFilesPerPlayer} audio files"
            )
        }

        val fileId = InputSanitizer.newFileId()
        val tmp = InputSanitizer.resolveContained(config.storageRoot, playerName, "$fileId.$extension.part")
        val target = InputSanitizer.resolveContained(config.storageRoot, playerName, "$fileId.$extension")
        val metaTarget = InputSanitizer.resolveContained(config.storageRoot, playerName, "$fileId.properties")

        var committed = false
        try {
            val sizeBytes = tmp.outputStream().use { out ->
                copyBounded(body, out, config.maxFileSizeBytes, onProgress)
            }

            beforeCommit()

            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE)

            val metadata = AudioMetadata(
                fileId = fileId,
                playerName = playerName,
                originalFileName = originalFileName,
                sizeBytes = sizeBytes,
                extension = extension,
                uploadedAt = Instant.now(),
                durationSeconds = durationSeconds,
                sampleRate = sampleRate
            )
            writeMetadata(metaTarget, metadata)
            committed = true
            return metadata
        } finally {
            if (!committed) {
                tmp.deleteIfExists()
                target.deleteIfExists()
                metaTarget.deleteIfExists()
            }
        }
    }

    fun rename(playerName: String, fileId: String, newName: String?): AudioMetadata? {
        val metaPath = InputSanitizer.resolveContained(config.storageRoot, playerName, "$fileId.properties")
        val meta = readMetadata(metaPath) ?: return null
        val cleaned = newName
            ?.filterNot(Char::isISOControl)
            ?.trim()
            ?.take(MAX_DISPLAY_NAME_LENGTH)
            ?.ifBlank { null }
        return meta.copy(customName = cleaned).also { writeMetadata(metaPath, it) }
    }

    fun resolveAudioFile(playerName: String, fileId: String, extension: String): Path {
        val target = InputSanitizer.resolveContained(config.storageRoot, playerName, "$fileId.$extension")
        if (!Files.isRegularFile(target)) throw NoSuchFileException(target.toString())
        return target
    }

    fun list(playerName: String): List<AudioMetadata> {
        val dir = playerDir(playerName)
        if (!dir.exists()) return emptyList()
        return Files.newDirectoryStream(dir, "*.properties").use { s ->
            s.mapNotNull(::readMetadata).sortedBy { it.uploadedAt }
        }
    }

    fun find(playerName: String, fileId: String): AudioMetadata? =
        readMetadata(InputSanitizer.resolveContained(config.storageRoot, playerName, "$fileId.properties"))

    fun delete(playerName: String, fileId: String): Boolean {
        val metaPath = InputSanitizer.resolveContained(config.storageRoot, playerName, "$fileId.properties")
        val meta = readMetadata(metaPath) ?: return false
        InputSanitizer.resolveContained(config.storageRoot, playerName, "$fileId.${meta.extension}").deleteIfExists()
        return metaPath.deleteIfExists()
    }

    private fun writeMetadata(path: Path, meta: AudioMetadata) {
        val props = Properties()
        props.setProperty("fileId", meta.fileId)
        props.setProperty("playerName", meta.playerName)
        props.setProperty("extension", meta.extension)
        meta.originalFileName?.let { props.setProperty("originalFileName", it) }
        meta.customName?.let { props.setProperty("customName", it) }
        props.setProperty("sizeBytes", meta.sizeBytes.toString())
        props.setProperty("uploadedAt", meta.uploadedAt.toString())
        props.setProperty("durationSeconds", meta.durationSeconds.toString())
        props.setProperty("sampleRate", meta.sampleRate.toString())

        val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
        try {
            tmp.outputStream().use { props.store(it, "CreateHarmonics audio metadata") }
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            tmp.deleteIfExists()
        }
    }
    private fun readMetadata(path: Path): AudioMetadata? = runCatching {
        val p = Properties().apply { path.inputStream().use(::load) }
        AudioMetadata(
            fileId = p.getProperty("fileId"),
            extension = p.getProperty("extension"),
            playerName = p.getProperty("playerName"),
            originalFileName = p.getProperty("originalFileName", null),
            customName = p.getProperty("customName", null),
            sizeBytes = p.getProperty("sizeBytes").toLong(),
            durationSeconds = p.getProperty("durationSeconds")?.toIntOrNull() ?: 0,
            sampleRate = p.getProperty("sampleRate")?.toIntOrNull() ?: 48000,
            uploadedAt = Instant.parse(p.getProperty("uploadedAt")),
        )
    }.getOrNull()

    private fun copyBounded(input: InputStream, output: OutputStream, limit: Long, onProgress: (Long) -> Unit): Long {
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
            onProgress(total)
        }
        return total
    }
}