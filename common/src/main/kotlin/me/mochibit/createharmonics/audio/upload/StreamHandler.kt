package me.mochibit.createharmonics.audio.upload

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.NoSuchFileException
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
 */
class StreamHandler(
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
            val extension = InputSanitizer.validateExtension(fileSegment.substring(dotIndex + 1))

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
                respond(exchange, file.toFile(), 200, 0, fileSize, isHead)
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
            respond(exchange, file.toFile(), 206, start, clampedEnd - start + 1, isHead)
        } catch (e: InvalidInputException) {
            exchange.sendPlainText(400, e.message ?: "Invalid request")
        } catch (e: NoSuchFileException) {
            exchange.sendPlainText(404, "Not Found")
        } catch (e: IOException) {
        } finally {
            exchange.close()
        }
    }

    private fun respond(
        exchange: HttpExchange,
        file: java.io.File,
        status: Int,
        start: Long,
        length: Long,
        isHead: Boolean,
    ) {
        if (isHead || length == 0L) {
            exchange.sendResponseHeaders(status, -1)
            return
        }
        exchange.sendResponseHeaders(status, length)
        RandomAccessFile(file, "r").use { raf ->
            writeChunk(exchange, raf, start, length)
        }
    }

    private fun writeChunk(exchange: HttpExchange, raf: RandomAccessFile, start: Long, length: Long) {
        raf.seek(start)
        val buffer = ByteArray(8 * 1024)
        var remaining = length
        exchange.responseBody.use { out ->
            while (remaining > 0) {
                val read = raf.read(buffer, 0, min(buffer.size.toLong(), remaining).toInt())
                if (read == -1) break
                out.write(buffer, 0, read)
                remaining -= read
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