package me.mochibit.createharmonics.audio.upload.client

import me.mochibit.createharmonics.audio.upload.AudioUploadServer
import me.mochibit.createharmonics.audio.upload.ServerAudioReference
import me.mochibit.createharmonics.foundation.eventbus.AutoHandler
import me.mochibit.createharmonics.foundation.eventbus.ModEventHandler
import me.mochibit.createharmonics.foundation.network.packet.AudioServerInfoPacket
import me.mochibit.createharmonics.foundation.services.eventService
import me.mochibit.createharmonics.foundation.services.networkService
import net.minecraft.client.Minecraft
import net.minecraft.server.level.ServerPlayer
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLEncoder

/**
 * On join send audio server port
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new OnJoinSendAudioServerPort
 */
@AutoHandler
object OnJoinSendAudioServerPort: ModEventHandler{
    override fun setupEvents() {
        eventService.onPlayerJoin { player ->
            if (player !is ServerPlayer || !AudioUploadServer.isRunning) return@onPlayerJoin
            networkService.sendToPlayer(player, AudioServerInfoPacket(AudioUploadServer.port, AudioUploadServer.host))
        }
    }

}


/**
 * Holder for determining the hostname and port for the audio server
 *
 * @constructor Creates a new AudioServerEndpoint
 */
object AudioServerEndpoint {
    @Volatile var port = 0
    @Volatile var publicHost = ""

    private fun resolveHost(): String = when {
        publicHost.isNotBlank() && publicHost !in WILDCARD_HOSTS -> publicHost
        Minecraft.getInstance().isLocalServer -> "127.0.0.1"
        else -> (Minecraft.getInstance().connection?.connection?.remoteAddress as? InetSocketAddress)
            ?.hostString ?: "127.0.0.1"
    }

    /**
     * Upload uri
     *
     * @param filename
     * @param durationSeconds the duration seconds
     * @param sampleRate the sample rate
     * @param title
     * @param artist
     * @return the uri
     */
    fun uploadUri(
        filename: String,
        durationSeconds: Int,
        sampleRate: Int,
        title: String? = null,
        artist: String? = null,
    ): URI {
        val query = buildList {
            add("filename=${enc(filename)}")
            add("durationSeconds=$durationSeconds")
            add("sampleRate=$sampleRate")
            title?.takeIf { it.isNotBlank() }?.let { add("title=${enc(it)}") }
            artist?.takeIf { it.isNotBlank() }?.let { add("artist=${enc(it)}") }
        }.joinToString("&")
        return URI("http://${resolveHost()}:${requirePort()}/audio/upload?$query")
    }

    /**
     * Info uri
     *
     * @param ref
     * @return the uri
     */
    fun infoUri(ref: ServerAudioReference): URI = URI("http://${resolveHost()}:${requirePort()}/audio/info/${enc(ref.owner)}/${enc(ref.fileId)}")

    /**
     * Stream url
     *
     * @param ref
     * @param extension
     * @return the string
     */
    fun streamUrl(ref: ServerAudioReference, extension: String): String =
        "http://${resolveHost()}:${requirePort()}/audio/stream/${enc(ref.owner)}/${enc(ref.fileId)}.$extension"

    private fun requirePort() = port.also { check(it > 0) { "Audio server unavailable" } }
    private fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8).replace("+", "%20")

    private val WILDCARD_HOSTS = setOf("0.0.0.0", "::", "[::]")
}


