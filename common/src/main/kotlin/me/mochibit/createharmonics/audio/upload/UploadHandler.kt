package me.mochibit.createharmonics.audio.upload

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * POST /audio/upload?player=<name>&ext=<ext>[&title=...][&artist=...][&filename=...]
 * Body: raw audio bytes.
 */
class UploadHandler(
    private val config: AudioUploadConfig,
    private val storage: AudioStorageManager,
) : HttpHandler {

    override fun handle(exchange: HttpExchange) {
        exchange.use { exchange ->
            if (exchange.requestMethod != "POST") {
                exchange.sendPlainText(405, "Method Not Allowed")
                return
            }

            val params = parseQuery(exchange.requestURI.rawQuery)

            try {
                val playerName = InputSanitizer.validatePlayerName(params["player"])
                val extension = InputSanitizer.validateExtension(params["ext"], config.allowedExtensions)
                val title = params["title"]?.take(256)
                val artist = params["artist"]?.take(256)
                val originalFileName = params["filename"]?.take(256)

                val contentLengthHeader = exchange.requestHeaders.getFirst("Content-Length")?.toLongOrNull()
                if (contentLengthHeader != null && contentLengthHeader > config.maxFileSizeBytes) {
                    exchange.sendPlainText(413, "File exceeds maximum allowed size of ${config.maxFileSizeBytes} bytes")
                    return
                }

                val metadata = storage.store(
                    playerName = playerName,
                    extension = extension,
                    originalFileName = originalFileName,
                    title = title,
                    artist = artist,
                    body = exchange.requestBody,
                )

                val body = "fileId=${metadata.fileId}\n" +
                        "streamPath=/audio/stream/$playerName/${metadata.fileId}.$extension\n" +
                        "sizeBytes=${metadata.sizeBytes}\n"
                exchange.sendPlainText(201, body)
            } catch (e: InvalidInputException) {
                exchange.sendPlainText(400, e.message ?: "Invalid request")
            } catch (e: QuotaExceededException) {
                exchange.sendPlainText(403, e.message ?: "Quota exceeded")
            } catch (e: FileTooLargeException) {
                exchange.sendPlainText(413, e.message ?: "File too large")
            } catch (e: IOException) {
                exchange.sendPlainText(500, "Internal error while storing the file")
            }
        }
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx < 0) return@mapNotNull null
            val key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8)
            val value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8)
            key to value
        }.toMap()
    }
}