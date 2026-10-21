package me.mochibit.createharmonics.audio.upload.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import me.mochibit.createharmonics.audio.upload.AudioMetadata
import me.mochibit.createharmonics.audio.upload.ServerAudioReference
import me.mochibit.createharmonics.config.ClientConfig
import me.mochibit.createharmonics.foundation.debug
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class AudioNotFoundException(ref: ServerAudioReference) : Exception("Audio $ref no longer exists on the server")
object AudioServerClient {
    private val http = HttpClient.newBuilder()
        .connectTimeout(3.seconds.toJavaDuration())
        .build()

    suspend fun fetchInfo(ref: ServerAudioReference): AudioMetadata = withContext(Dispatchers.IO) {
        val uri = AudioServerEndpoint.infoUri(ref)

        if (ClientConfig.debugAudioPlayer.get()) {
            "[Audio Server Client] Requesting info to :${uri}".debug()
        }

        val request = HttpRequest.newBuilder(uri)
            .timeout(3.seconds.toJavaDuration())
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 404) throw AudioNotFoundException(ref)

        check(response.statusCode() == 200) { "Audio server returned ${response.statusCode()}" }
        Json.decodeFromString<AudioMetadata>(response.body())
    }

    suspend fun download(url: String, target: Path) = withContext(Dispatchers.IO) {
        val response = http.send(HttpRequest.newBuilder(URI(url)).GET().build(), HttpResponse.BodyHandlers.ofInputStream())
        check(response.statusCode() == 200) { "HTTP ${response.statusCode()}" }
        response.body().use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
        val expected = response.headers().firstValueAsLong("Content-Length")
        check(!expected.isPresent || expected.asLong == Files.size(target)) { "Truncated download" }
    }
}