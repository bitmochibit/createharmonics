package me.mochibit.createharmonics.foundation.eventbus

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.Connection
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor

/**
 * This event type is used to register platform specific event handlers that are required even for the common layer
 */
sealed interface ProxyEvent : ModEvent {
    data class ServerStartedEventProxy(
        val server: MinecraftServer,
    ) : ProxyEvent

    data class ServerStoppedEventProxy(
        val server: MinecraftServer,
    ) : ProxyEvent

    data class ClientDisconnectedEventProxy(
        val controller: MultiPlayerGameMode?,
        val localPlayer: LocalPlayer?,
        val networkManager: Connection?,
    ) : ProxyEvent

    data class EntityJoinLevelEventProxy(
        val entity: Entity,
        val level: Level,
    ) : ProxyEvent

    data class RegisterCommandsEventProxy(
        val dispatcher: CommandDispatcher<CommandSourceStack>,
        val env: Commands.CommandSelection,
        val context: CommandBuildContext,
    ) : ProxyEvent

    data class LevelUnloadEventProxy(
        val levelAccess: LevelAccessor,
    ) : ProxyEvent

    class GameShuttingDownEventProxy : ProxyEvent
}
