package me.mochibit.createharmonics.foundation.services

import me.mochibit.createharmonics.foundation.network.packet.ModPacket
import net.minecraft.server.level.ServerPlayer

interface NetworkService {
    fun sendToServer(packet: ModPacket)

    fun sendToPlayer(
        player: ServerPlayer,
        packet: ModPacket,
    )

    fun broadcast(packet: ModPacket)
}

val networkService: NetworkService by lazy {
    loadService<NetworkService>()
}
