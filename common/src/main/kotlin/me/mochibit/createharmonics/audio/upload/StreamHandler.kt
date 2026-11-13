package me.mochibit.createharmonics.audio.upload

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.math.min

private val AUDIO_MIME_TYPES = mapOf(
    "mp3" to "audio/mpeg",
    "wav" to "audio/wav",
    "ogg" to "audio/ogg",
    "oga" to "audio/ogg",
    "opus" to "audio/opus",
    "flac" to "audio/flac",
    "m4a" to "audio/mp4",
    "aac" to "audio/aac",
    "weba" to "audio/webm",
)

/**
 * GET/HEAD /audio/stream/<playerName>/<fileId>
 *
 * @constructor Creates a new StreamHandler
 * @property storageProvider the storage provider
 */
class StreamHandler(
    private val storageProvider: () -> AudioStorageManager,
) : HttpHandler {

    override fun handle(exchange: HttpExchange) {
        val storage = storageProvider()
        try {
            if (exchange.requestMethod != "GET" && exchange.requestMethod != "HEAD") {
                exchange.sendPlainText(405, AudioUploadResponse.INVALID_METHOD.name)
                return
            }

            val segments = exchange.requestURI.path
                .removePrefix("/audio/stream/")
                .split("/")
                .filter { it.isNotBlank() }

            if (segments.size != 2) {
                exchange.sendPlainText(404, AudioUploadResponse.FILE_NOT_FOUND.name)
                return
            }

            val (playerSegment, fileSegment) = segments
            val dotIndex = fileSegment.lastIndexOf('.')
            if (dotIndex <= 0) {
                exchange.sendPlainText(404, AudioUploadResponse.FILE_NOT_FOUND.name)
                return
            }

            val playerName = InputSanitizer.validatePlayerName(playerSegment)
            val fileId = InputSanitizer.validateFileId(fileSegment.substringBeforeLast('.'))

            val meta = storage.find(playerName, fileId) ?: run {
                exchange.sendPlainText(404, AudioUploadResponse.FILE_NOT_FOUND.name); return
            }
            val extension = meta.extension
            val file = storage.resolveAudioFile(playerName, fileId, extension)
            val fileSize = Files.size(file)
            val isHead = exchange.requestMethod == "HEAD"

            exchange.responseHeaders.apply {
                set("Content-Type", AUDIO_MIME_TYPES[extension] ?: "application/octet-stream")
                set("X-Content-Type-Options", "nosniff")
                set("Accept-Ranges", "bytes")
            }

            val range = exchange.requestHeaders.getFirst("Range")?.let { parseRange(it, fileSize) }

            if (range == null) {
                respond(exchange, file, 200, 0, fileSize, isHead)
                return
            }

            val (start, end) = range
            if (start > end || start >= fileSize) {
                exchange.responseHeaders.set("Content-Range", "bytes */$fileSize")
                exchange.sendResponseHeaders(416, -1)
                return
            }

            val clampedEnd = min(end, fileSize - 1)
            exchange.responseHeaders.set("Content-Range", "bytes $start-$clampedEnd/$fileSize")
            respond(exchange, file, 206, start, clampedEnd - start + 1, isHead)
        } catch (e: InvalidInputException) {
            e.printStackTrace()
            exchange.sendPlainText(400, AudioUploadResponse.INVALID_INPUT.name)
        } catch (e: NoSuchFileException) {
            exchange.sendPlainText(404, AudioUploadResponse.FILE_NOT_FOUND.name)
        } catch (e: IOException) {
        } finally {
            exchange.close()
        }
    }

    private fun respond(
        exchange: HttpExchange,
        file: Path,
        status: Int,
        start: Long,
        length: Long,
        isHead: Boolean,
    ) {
        if (isHead || length == 0L) {
            exchange.sendResponseHeaders(status, -1)
            return
        }
        FileChannel.open(file, StandardOpenOption.READ).use { channel ->
            exchange.sendResponseHeaders(status, length)
            exchange.responseBody.use { out ->
                val target = Channels.newChannel(out)
                var position = start
                var remaining = length
                while (remaining > 0) {
                    val sent = channel.transferTo(position, remaining, target)
                    if (sent <= 0) break
                    position += sent
                    remaining -= sent
                }
            }
        }
    }

    private fun parseRange(header: String, fileSize: Long): Pair<Long, Long>? {
        if (!header.startsWith("bytes=")) return null
        val parts = header.removePrefix("bytes=").substringBefore(',').split("-")
        if (parts.size != 2) return null

        val startStr = parts[0].trim()
        val endStr = parts[1].trim()

        return when {
            startStr.isNotEmpty() && endStr.isNotEmpty() -> {
                val s = startStr.toLongOrNull() ?: return null
                val e = endStr.toLongOrNull() ?: return null
                s to e
            }
            startStr.isNotEmpty() -> startStr.toLongOrNull()?.let { it to (fileSize - 1) }
            endStr.isNotEmpty() -> endStr.toLongOrNull()?.let { suffixLen ->
                (fileSize - suffixLen).coerceAtLeast(0) to (fileSize - 1)
            }
            else -> null
        }
    }
}


