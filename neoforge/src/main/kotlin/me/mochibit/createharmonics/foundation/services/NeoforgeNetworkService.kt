package me.mochibit.createharmonics.foundation.services

import me.mochibit.createharmonics.foundation.network.packet.ModPacket
import me.mochibit.createharmonics.foundation.registry.NeoforgeModPackets
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.network.PacketDistributor

class NeoforgeNetworkService : NetworkService {
    override fun sendToServer(packet: ModPacket) {
        PacketDistributor.sendToServer(NeoforgeModPackets.payloadFor(packet))
    }

    override fun sendToPlayer(
        player: ServerPlayer,
        packet: ModPacket,
    ) {
        PacketDistributor.sendToPlayer(player, NeoforgeModPackets.payloadFor(packet))
    }

    override fun sendToTrackingEntity(
        packet: ModPacket,
        entity: Entity,
    ) {
        PacketDistributor.sendToPlayersTrackingEntity(entity, NeoforgeModPackets.payloadFor(packet))
    }

    override fun broadcast(packet: ModPacket) {
        PacketDistributor.sendToAllPlayers(NeoforgeModPackets.payloadFor(packet))
    }
}
