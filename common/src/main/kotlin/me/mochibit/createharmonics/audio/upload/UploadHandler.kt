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
//todo: token system for handling secure requests
class UploadHandler(private val storage: AudioStorageManager) : HttpHandler {

    override fun handle(exchange: HttpExchange) {
        exchange.use { exchange ->
            if (exchange.requestMethod != "POST") {
                exchange.sendPlainText(405, "Method Not Allowed"); return
            }

            val ticket = exchange.bearerToken()?.let(UploadTicketRegistry::consume)
            if (ticket == null) {
                exchange.sendPlainText(401, "Invalid or expired upload ticket"); return
            }

            val params = parseQuery(exchange.requestURI.rawQuery)
            val maxSize = storage.currentConfig().maxFileSizeBytes

            try {
                val declared = exchange.requestHeaders.getFirst("Content-Length")?.toLongOrNull()
                if (declared != null && declared > maxSize) {
                    exchange.sendPlainText(413, "File exceeds maximum allowed size of $maxSize bytes"); return
                }

                val originalFileName = params["filename"]?.take(256)
                val extension = InputSanitizer.extensionFrom(originalFileName)

                val metadata = storage.store(
                    playerName = ticket.playerName,
                    originalFileName = originalFileName,
                    extension = extension,
                    title = params["title"]?.take(256),
                    artist = params["artist"]?.take(256),
                    body = exchange.requestBody,
                )

                exchange.sendPlainText(
                    201,
                    "fileId=${metadata.fileId}\n" +
                            "streamPath=/audio/stream/${ticket.playerName}/${metadata.fileId}.${metadata.extension}\n" +
                            "sizeBytes=${metadata.sizeBytes}\n"
                )
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

    private fun HttpExchange.bearerToken(): String? =
        requestHeaders.getFirst("Authorization")
            ?.takeIf { it.startsWith("Bearer ") }
            ?.removePrefix("Bearer ")?.trim()
}