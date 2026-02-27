package me.mochibit.createharmonics.foundation.services

import me.mochibit.createharmonics.foundation.network.packet.ModPacket
import me.mochibit.createharmonics.foundation.registry.ForgeModPackets
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.network.PacketDistributor

class ForgeNetworkService : NetworkService {
    override fun sendToServer(packet: ModPacket) {
        ForgeModPackets.channel.sendToServer(packet)
    }

    override fun sendToPlayer(
        player: ServerPlayer,
        packet: ModPacket,
    ) {
        ForgeModPackets.channel.send(PacketDistributor.PLAYER.with { player }, packet)
    }

    override fun broadcast(packet: ModPacket) {
        ForgeModPackets.channel.send(PacketDistributor.ALL.noArg(), packet)
    }
}
