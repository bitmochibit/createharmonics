package me.mochibit.createharmonics.foundation.registry

import dev.architectury.networking.NetworkManager
import io.netty.buffer.Unpooled
import me.mochibit.createharmonics.foundation.extension.asResource
import me.mochibit.createharmonics.foundation.network.ModPacket
import me.mochibit.createharmonics.foundation.network.packet.AudioPlayerContextStopPacket
import me.mochibit.createharmonics.foundation.network.packet.AudioPlayerStreamEndPacket
import me.mochibit.createharmonics.foundation.network.packet.UpdateAudioNamePacket
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

object ModPackets : AutoRegistrable {
    private val clientToServer: Map<ResourceLocation, PacketType<*>> =
        mapOf(
            "audio_stream_end".asResource() to PacketType(::AudioPlayerStreamEndPacket),
            "update_audio_name".asResource() to PacketType(::UpdateAudioNamePacket),
        )

    private val serverToClient: Map<ResourceLocation, PacketType<*>> =
        mapOf(
            "audio_context_stop".asResource() to PacketType(::AudioPlayerContextStopPacket),
        )

    private val c2sIds: Map<Class<*>, ResourceLocation> by lazy {
        clientToServer.entries.associate { (id, pt) -> pt.packetClass to id }
    }
    private val s2cIds: Map<Class<*>, ResourceLocation> by lazy {
        serverToClient.entries.associate { (id, pt) -> pt.packetClass to id }
    }

    override fun register() {
        registerServer()
        registerClient()
    }

    fun registerServer() {
        clientToServer.forEach { (id, packetType) -> packetType.registerC2S(id) }
    }

    fun registerClient() {
        serverToClient.forEach { (id, packetType) -> packetType.registerS2C(id) }
    }

    fun sendToServer(packet: ModPacket) {
        val id =
            c2sIds[packet::class.java]
                ?: throw IllegalArgumentException("${packet::class.java} non registrato C2S")
        NetworkManager.sendToServer(id, FriendlyByteBuf(Unpooled.buffer()).also(packet::write))
    }

    fun sendToPlayer(
        player: ServerPlayer,
        packet: ModPacket,
    ) {
        val id =
            s2cIds[packet::class.java]
                ?: throw IllegalArgumentException("${packet::class.java} non registrato S2C")
        NetworkManager.sendToPlayer(player, id, FriendlyByteBuf(Unpooled.buffer()).also(packet::write))
    }
}

private inline fun <reified T : ModPacket> PacketType(noinline factory: (FriendlyByteBuf) -> T) = PacketType(T::class.java, factory)

private class PacketType<T : ModPacket>(
    val packetClass: Class<*> = factory(FriendlyByteBuf(Unpooled.buffer()))::class.java,
    val factory: (FriendlyByteBuf) -> T,
) {
    fun registerC2S(id: ResourceLocation) {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, id) { buf, context ->
            val packet = factory(buf)
            context.queue { packet.handle(context.player as? ServerPlayer) }
        }
    }

    fun registerS2C(id: ResourceLocation) {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, id) { buf, context ->
            val packet = factory(buf)
            context.queue { packet.handle(null) }
        }
    }
}
