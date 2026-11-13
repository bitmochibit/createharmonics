package me.mochibit.createharmonics.foundation.services

import me.mochibit.createharmonics.foundation.network.packet.C2SPacket
import me.mochibit.createharmonics.foundation.network.packet.ModPacket
import me.mochibit.createharmonics.foundation.network.packet.S2CPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity

/**
 * Network service
 *
 */
interface NetworkService {
    /**
     * Sends the to server
     *
     * @param packet
     */
    fun sendToServer(packet: C2SPacket)

    /**
     * Sends the to player
     *
     * @param player
     * @param packet
     */
    fun sendToPlayer(
        player: ServerPlayer,
        packet: S2CPacket,
    )

    /**
     * Sends the to tracking entity
     *
     * @param packet
     * @param entity
     */
    fun sendToTrackingEntity(
        packet: S2CPacket,
        entity: Entity,
    )

    /**
     * Broadcast
     *
     * @param packet
     */
    fun broadcast(packet: S2CPacket)
}

val networkService: NetworkService by lazy {
    loadService<NetworkService>()
}
