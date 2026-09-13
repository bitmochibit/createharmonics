package me.mochibit.createharmonics.audio.upload

import com.sun.net.httpserver.HttpExchange
import java.nio.charset.StandardCharsets

internal fun HttpExchange.sendPlainText(status: Int, body: String) {
    val bytes = body.toByteArray(StandardCharsets.UTF_8)
    responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}