package me.mochibit.createharmonics.audio.upload

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import kotlinx.serialization.json.Json
import me.mochibit.createharmonics.foundation.warn
import java.io.IOException


/** GET /audio/info/<playerName>/<fileId> */
class InfoHandler(private val storageProvider: () -> AudioStorageManager) : HttpHandler {
    override fun handle(exchange: HttpExchange) {
        try {
            if (exchange.requestMethod != "GET") return exchange.sendPlainText(405, "Method Not Allowed")

            val segments = exchange.requestURI.path.removePrefix("/audio/info/").split("/").filter { it.isNotBlank() }
            if (segments.size != 2) return exchange.sendPlainText(404, "Not Found")

            val owner = InputSanitizer.validatePlayerName(segments[0])
            val fileId = InputSanitizer.validateFileId(segments[1])

            val meta = storageProvider().find(owner, fileId)
                ?: return exchange.sendPlainText(404, "Not Found")

            val body = Json.encodeToString(meta).toByteArray()

            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        } catch (e: InvalidInputException) {
            exchange.sendPlainText(400, e.message ?: "Invalid request")
        } catch (e: IOException) {
        } catch (e: Exception) {
            "Audio server handler failed: ${e.stackTraceToString()}".warn()
            runCatching { exchange.sendPlainText(500, "Internal error") }
        } finally {
            exchange.close()
        }
    }
}