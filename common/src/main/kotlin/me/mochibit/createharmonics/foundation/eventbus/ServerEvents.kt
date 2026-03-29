package me.mochibit.createharmonics.foundation.eventbus

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity

object ServerEvents {
    data class ServerStartedEvent(
        val server: MinecraftServer,
    ) : ServerProxyEvent

    data class ServerStoppedEvent(
        val server: MinecraftServer,
    ) : ServerProxyEvent

    data class PlayerStartTrackingEntity(
        val player: ServerPlayer,
        val entity: Entity,
    ) : ServerProxyEvent
}
