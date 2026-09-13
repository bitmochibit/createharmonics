package me.mochibit.createharmonics.audio.upload

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import kotlin.math.min

//todo configurable and dynamic
private val MIME_TYPES = mapOf(
    "mp3" to "audio/mpeg",
    "wav" to "audio/wav",
    "ogg" to "audio/ogg",
    "flac" to "audio/flac",
    "m4a" to "audio/mp4",
    "opus" to "audio/opus",
)

/**
 * GET/HEAD /audio/stream/<playerName>/<fileId>.<ext>
 * Supports HTTP Range requests so ffmpeg (and any other HTTP-capable
 * consumer) can pull the file as a plain byte-range-seekable stream.
 */
class StreamHandler(
    private val config: AudioUploadConfig,
    private val storage: AudioStorageManager,
) : HttpHandler {

    override fun handle(exchange: HttpExchange) {
        try {
            if (exchange.requestMethod != "GET" && exchange.requestMethod != "HEAD") {
                exchange.sendPlainText(405, "Method Not Allowed")
                return
            }

            val segments = exchange.requestURI.path
                .removePrefix("/audio/stream/")
                .split("/")
                .filter { it.isNotBlank() }

            if (segments.size != 2) {
                exchange.sendPlainText(404, "Not Found")
                return
            }

            val (playerSegment, fileSegment) = segments
            val dotIndex = fileSegment.lastIndexOf('.')
            if (dotIndex <= 0) {
                exchange.sendPlainText(404, "Not Found")
                return
            }

            val playerName = InputSanitizer.validatePlayerName(playerSegment)
            val fileId = InputSanitizer.validateFileId(fileSegment.substring(0, dotIndex))
            val extension = InputSanitizer.validateExtension(fileSegment.substring(dotIndex + 1), config.allowedExtensions)

            val file = storage.resolveAudioFile(playerName, fileId, extension)
            val fileSize = Files.size(file)
            val mimeType = MIME_TYPES[extension] ?: "application/octet-stream"

            exchange.responseHeaders.set("Content-Type", mimeType)
            exchange.responseHeaders.set("Accept-Ranges", "bytes")

            val range = exchange.requestHeaders.getFirst("Range")?.let { parseRange(it, fileSize) }
            val isHead = exchange.requestMethod == "HEAD"

            RandomAccessFile(file.toFile(), "r").use { raf ->
                if (range == null) {
                    exchange.sendResponseHeaders(200, if (isHead) -1 else fileSize)
                    if (!isHead) writeChunk(exchange, raf, 0, fileSize) else exchange.responseBody.close()
                } else {
                    val (start, end) = range
                    if (start > end || start >= fileSize) {
                        exchange.responseHeaders.set("Content-Range", "bytes */$fileSize")
                        exchange.sendResponseHeaders(416, -1)
                        exchange.responseBody.close()
                        return
                    }
                    val clampedEnd = min(end, fileSize - 1)
                    val length = clampedEnd - start + 1
                    exchange.responseHeaders.set("Content-Range", "bytes $start-$clampedEnd/$fileSize")
                    exchange.sendResponseHeaders(206, if (isHead) -1 else length)
                    if (!isHead) writeChunk(exchange, raf, start, length) else exchange.responseBody.close()
                }
            }
        } catch (e: InvalidInputException) {
            exchange.sendPlainText(400, e.message ?: "Invalid request")
        } catch (e: NoSuchFileException) {
            exchange.sendPlainText(404, "Not Found")
        } finally {
            exchange.close()
        }
    }

    private fun writeChunk(exchange: HttpExchange, raf: RandomAccessFile, start: Long, length: Long) {
        raf.seek(start)
        val buffer = ByteArray(8 * 1024)
        var remaining = length
        exchange.responseBody.use { out ->
            while (remaining > 0) {
                val toRead = min(buffer.size.toLong(), remaining).toInt()
                val read = raf.read(buffer, 0, toRead)
                if (read == -1) break
                out.write(buffer, 0, read)
                remaining -= read
            }
        }
    }

    /** Parses a single-range "bytes=start-end" header */
    private fun parseRange(header: String, fileSize: Long): Pair<Long, Long>? {
        if (!header.startsWith("bytes=")) return null
        val spec = header.removePrefix("bytes=").substringBefore(',')
        val parts = spec.split("-")
        if (parts.size != 2) return null
        val startStr = parts[0].trim()
        val endStr = parts[1].trim()
        return when {
            startStr.isNotEmpty() && endStr.isNotEmpty() ->
                startStr.toLongOrNull()?.let { s -> endStr.toLongOrNull()?.let { e -> s to e } }
            startStr.isNotEmpty() && endStr.isEmpty() ->
                startStr.toLongOrNull()?.let { s -> s to (fileSize - 1) }
            startStr.isEmpty() && endStr.isNotEmpty() ->
                endStr.toLongOrNull()?.let { suffixLen -> (fileSize - suffixLen).coerceAtLeast(0) to (fileSize - 1) }
            else -> null
        }
    }
}