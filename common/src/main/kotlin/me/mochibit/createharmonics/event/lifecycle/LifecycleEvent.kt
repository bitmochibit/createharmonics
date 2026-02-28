package me.mochibit.createharmonics.event.lifecycle

import me.mochibit.createharmonics.foundation.eventbus.ModEvent
import net.minecraft.server.MinecraftServer

data class ServerStartedEvent(
    val server: MinecraftServer,
) : ModEvent

data class ServerStoppedEvent(
    val server: MinecraftServer,
) : ModEvent
