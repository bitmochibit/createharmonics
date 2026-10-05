package me.mochibit.createharmonics.foundation.services

import me.mochibit.createharmonics.foundation.network.packet.C2SPacket
import me.mochibit.createharmonics.foundation.network.packet.ModPacket
import me.mochibit.createharmonics.foundation.network.packet.S2CPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity

interface NetworkService {
    fun sendToServer(packet: C2SPacket)

    fun sendToPlayer(
        player: ServerPlayer,
        packet: S2CPacket,
    )

    fun sendToTrackingEntity(
        packet: S2CPacket,
        entity: Entity,
    )

    fun broadcast(packet: S2CPacket)
}

val networkService: NetworkService by lazy {
    loadService<NetworkService>()
}
