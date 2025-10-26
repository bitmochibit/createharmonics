package me.mochibit.createharmonics.network

import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.network.ModNetworkHandler.channel
import me.mochibit.createharmonics.network.RemoveModAudioPlayerPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.PacketDistributor
import net.minecraftforge.network.simple.IndexedMessageCodec
import net.minecraftforge.network.simple.SimpleChannel

object ModNetworkHandler {
    private const val PROTOCOL_VERSION = "1"
    private var packetId = 0

    val channel: SimpleChannel = NetworkRegistry.newSimpleChannel(
        ResourceLocation.fromNamespaceAndPath(CreateHarmonicsMod.MOD_ID, "main"),
        { PROTOCOL_VERSION },
        { it == PROTOCOL_VERSION },
        { it == PROTOCOL_VERSION }
    )

    fun register() {
        channel.messageBuilder(RemoveModAudioPlayerPacket::class.java, packetId++)
            .encoder(RemoveModAudioPlayerPacket::encode)
            .decoder(RemoveModAudioPlayerPacket::decode)
            .consumerMainThread(RemoveModAudioPlayerPacket::handle)
            .add()
    }

    /**
     * Sends a packet to a specific player
     */
    fun sendToPlayer(packet: RemoveModAudioPlayerPacket, player: ServerPlayer) {
        channel.send(PacketDistributor.PLAYER.with { player }, packet)
    }

    /**
     * Sends a packet to all players
     */
    fun sendToAll(packet: RemoveModAudioPlayerPacket) {
        channel.send(PacketDistributor.ALL.noArg(), packet)
    }

}