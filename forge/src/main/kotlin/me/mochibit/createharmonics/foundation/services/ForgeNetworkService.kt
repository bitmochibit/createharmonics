package me.mochibit.createharmonics.foundation.services

import me.mochibit.createharmonics.foundation.network.packet.ContraptionBlockDataChangedPacket
import me.mochibit.createharmonics.foundation.network.packet.ModPacket
import me.mochibit.createharmonics.foundation.registry.ForgeModPackets
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
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

    override fun sendToTrackingEntity(
        packet: ModPacket,
        entity: Entity,
    ) {
        ForgeModPackets.channel.send(
            PacketDistributor.TRACKING_ENTITY.with { entity },
            packet,
        )
    }

    override fun broadcast(packet: ModPacket) {
        ForgeModPackets.channel.send(PacketDistributor.ALL.noArg(), packet)
    }
}
