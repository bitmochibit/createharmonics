package me.mochibit.createharmonics.event.proxy

import com.mojang.brigadier.CommandDispatcher
import me.mochibit.createharmonics.foundation.eventbus.ProxyEvent
import me.mochibit.createharmonics.foundation.services.platformService
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.Connection
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.LevelAccessor

data class ServerStartedEventProxy(
    val server: MinecraftServer,
    override val proxyRegistrar: () -> Unit = platformService::serverStartedEventProxy,
) : ProxyEvent

data class ServerStoppedEventProxy(
    val server: MinecraftServer,
    override val proxyRegistrar: () -> Unit = platformService::serverStoppedEventProxy,
) : ProxyEvent

data class ClientDisconnectedEventProxy(
    val controller: MultiPlayerGameMode,
    val localPlayer: LocalPlayer,
    val networkManager: Connection,
    override val proxyRegistrar: () -> Unit = platformService::clientDisconnectedEventProxy,
) : ProxyEvent

data class RegisterCommandsEventProxy(
    val dispatcher: CommandDispatcher<CommandSourceStack>,
    val env: Commands.CommandSelection,
    val context: CommandBuildContext,
    override val proxyRegistrar: () -> Unit = platformService::registerCommandsEventProxy,
) : ProxyEvent

data class LevelUnloadEventProxy(
    val levelAccess: LevelAccessor,
    override val proxyRegistrar: () -> Unit = platformService::levelUnloadEventProxy,
) : ProxyEvent

data class GameShuttingDownEventProxy(
    override val proxyRegistrar: () -> Unit = platformService::levelUnloadEventProxy,
) : ProxyEvent
