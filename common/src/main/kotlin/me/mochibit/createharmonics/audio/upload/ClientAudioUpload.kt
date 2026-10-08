package me.mochibit.createharmonics.audio.upload

import me.mochibit.createharmonics.foundation.info
import me.mochibit.createharmonics.foundation.network.packet.AudioUploadGrantedPacket
import me.mochibit.createharmonics.foundation.network.packet.RequestAudioUpload
import me.mochibit.createharmonics.foundation.services.networkService
import me.mochibit.createharmonics.foundation.warn
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Duration

object ClientAudioUpload {
    data class PendingUpload(
        val file: Path,
        val pos: BlockPos,
        val title: String? = null,
        val artist: String? = null
    )
    private val http = HttpClient.newHttpClient()
    @Volatile private var pending: PendingUpload? = null

    @Volatile var isBusy = false
        private set

    fun begin(upload: PendingUpload) {
        if (isBusy) return
        isBusy = true
        pending = upload
        networkService.sendToServer(
            RequestAudioUpload(upload.pos, upload.file.fileName.toString())
        )
    }

    fun onDenied(reason: String) {
        pending = null
        isBusy = false
        reason.warn()
    }

    fun onGranted(grant: AudioUploadGrantedPacket) {
        val upload = pending?.also { pending = null } ?: return
        val mc = Minecraft.getInstance()

        val host = (mc.connection?.connection?.remoteAddress as? InetSocketAddress)?.hostString
            ?: "127.0.0.1"
        val hostPart = if (':' in host) "[$host]" else host

        fun enc(s: String) = URLEncoder.encode(s, StandardCharsets.UTF_8)
        val query = listOfNotNull(
            "filename=${enc(upload.file.fileName.toString())}",
            upload.title?.let { "title=${enc(it)}" },
            upload.artist?.let { "artist=${enc(it)}" },
        ).joinToString("&")

        val request = try {
            HttpRequest.newBuilder(URI.create("http://$hostPart:${grant.port}/audio/upload?$query"))
                .header("Authorization", "Bearer ${grant.token}")
                .timeout(Duration.ofMinutes(5))
                .POST(HttpRequest.BodyPublishers.ofFile(upload.file))
                .build()
        } catch (e: IOException) {
            isBusy = false
            "Can't read the file ${e.message}".warn()
            return
        }

        http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .whenComplete { resp, err ->
                isBusy = false
                when {
                    err != null -> "Upload failed: ${err.message}".warn()
                    resp.statusCode() != 201 -> "Upload failed (${resp.statusCode()}): ${resp.body()}".warn()
                    else -> {
                        "Upload completed".info()
                    }
                }
            }
    }

}