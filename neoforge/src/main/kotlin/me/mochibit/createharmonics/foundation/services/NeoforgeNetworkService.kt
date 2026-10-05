package me.mochibit.createharmonics.foundation.services

import me.mochibit.createharmonics.foundation.network.packet.C2SPacket
import me.mochibit.createharmonics.foundation.network.packet.ModPacket
import me.mochibit.createharmonics.foundation.network.packet.S2CPacket
import me.mochibit.createharmonics.foundation.registry.NeoforgeModPackets
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.server.ServerLifecycleHooks

class NeoforgeNetworkService : NetworkService {
    override fun sendToServer(packet: C2SPacket) {
        PacketDistributor.sendToServer(NeoforgeModPackets.payloadFor(packet))
    }

    override fun sendToPlayer(player: ServerPlayer, packet: S2CPacket) {
        PacketDistributor.sendToPlayer(player, NeoforgeModPackets.payloadFor(packet))
    }

    override fun sendToTrackingEntity(packet: S2CPacket, entity: Entity) {
        PacketDistributor.sendToPlayersTrackingEntity(entity, NeoforgeModPackets.payloadFor(packet))
    }

    override fun broadcast(packet: S2CPacket) {
        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        if (!server.isRunning) return
        PacketDistributor.sendToAllPlayers(NeoforgeModPackets.payloadFor(packet))
    }
}
