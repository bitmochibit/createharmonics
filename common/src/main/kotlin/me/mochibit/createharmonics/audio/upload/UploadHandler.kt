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
class UploadHandler(private val storage: AudioStorageManager) : HttpHandler {

    override fun handle(exchange: HttpExchange) = exchange.use { ex ->
        if (ex.requestMethod != "POST") {
            ex.sendPlainText(405, "Method Not Allowed"); return@use
        }

        val ticket = ex.bearerToken()?.let(UploadTicketRegistry::consume)
        if (ticket == null) {
            ex.sendPlainText(401, "Invalid or expired upload ticket"); return@use
        }

        try {
            val params = parseQuery(ex.requestURI.rawQuery)
            val maxSize = storage.currentConfig().maxFileSizeBytes
            val declared = ex.requestHeaders.getFirst("Content-Length")?.toLongOrNull()

            if (declared != null && declared > maxSize) {
                ex.sendPlainText(413, "File exceeds maximum allowed size of $maxSize bytes"); return@use
            }

            val originalFileName = params["filename"]?.take(256)
            val metadata = storage.store(
                playerName = ticket.playerName,
                originalFileName = originalFileName,
                extension = InputSanitizer.extensionFrom(originalFileName),
                title = params["title"]?.take(256),
                artist = params["artist"]?.take(256),
                body = ex.requestBody,
                onProgress = { read ->
                    if (declared != null && declared > 0) {
                        ticket.uploadSession.progress = (read.toFloat() / declared).coerceIn(0f, 1f)
                    }
                },
            )

            ex.sendPlainText(
                201,
                "fileId=${metadata.fileId}\n" +
                        "streamPath=/audio/stream/${ticket.playerName}/${metadata.fileId}.${metadata.extension}\n" +
                        "sizeBytes=${metadata.sizeBytes}\n",
            )
        } catch (e: InvalidInputException) {
            ex.sendPlainText(400, e.message ?: "Invalid request")
        } catch (e: QuotaExceededException) {
            ex.sendPlainText(403, e.message ?: "Quota exceeded")
        } catch (e: FileTooLargeException) {
            ex.sendPlainText(413, e.message ?: "File too large")
        } catch (e: IOException) {
            ex.sendPlainText(500, "Internal error while storing the file")
        } finally {
            ticket.uploadSession.finish()
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