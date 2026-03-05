package me.mochibit.createharmonics.foundation.services

import me.mochibit.createharmonics.ModEventBus
import me.mochibit.createharmonics.event.proxy.ClientDisconnectedEventProxy
import me.mochibit.createharmonics.event.proxy.GameShuttingDownEventProxy
import me.mochibit.createharmonics.event.proxy.LevelUnloadEventProxy
import me.mochibit.createharmonics.event.proxy.RegisterCommandsEventProxy
import me.mochibit.createharmonics.event.proxy.ServerStartedEventProxy
import me.mochibit.createharmonics.event.proxy.ServerStoppedEventProxy
import me.mochibit.createharmonics.foundation.eventbus.EventBus
import me.mochibit.createharmonics.foundation.registry.registerEventProxy
import net.minecraftforge.client.event.ClientPlayerNetworkEvent
import net.minecraftforge.event.GameShuttingDownEvent
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.event.level.LevelEvent
import net.minecraftforge.event.server.ServerStartedEvent
import net.minecraftforge.event.server.ServerStoppedEvent
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.loading.FMLLoader

class ForgePlatformService : PlatformService {
    override val platformName: String = "Forge"
    override val environment: PlatformService.Environment
        get() =
            when {
                FMLLoader.getDist().isClient -> PlatformService.Environment.CLIENT
                FMLLoader.getDist().isDedicatedServer -> PlatformService.Environment.SERVER
                else -> throw IllegalStateException("Unknown environment")
            }

    override fun isModLoaded(modId: String): Boolean = ModList.get().isLoaded(modId)

    override fun serverStartedEventProxy() {
        registerEventProxy<ServerStartedEvent> {
            ServerStartedEventProxy(this.server)
        }
    }

    override fun serverStoppedEventProxy() {
        registerEventProxy<ServerStoppedEvent> {
            ServerStoppedEventProxy(this.server)
        }
    }

    override fun clientDisconnectedEventProxy() {
        registerEventProxy<ClientPlayerNetworkEvent.LoggingOut> {
            ClientDisconnectedEventProxy(this.multiPlayerGameMode!!, this.player!!, this.connection!!)
        }
    }

    override fun registerCommandsEventProxy() {
        registerEventProxy<RegisterCommandsEvent> {
            RegisterCommandsEventProxy(this.dispatcher, this.commandSelection, this.buildContext)
        }
    }

    override fun levelUnloadEventProxy() {
        registerEventProxy<LevelEvent.Unload> {
            LevelUnloadEventProxy(this.level)
        }
    }

    override fun gameShuttingDownEventProxy() {
        registerEventProxy<GameShuttingDownEvent> {
            GameShuttingDownEventProxy()
        }
    }
}
