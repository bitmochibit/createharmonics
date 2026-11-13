package me.mochibit.createharmonics.foundation.registry

import kotlinx.coroutines.Dispatchers
import me.mochibit.createharmonics.CreateHarmonicsMod.MOD_ID
import me.mochibit.createharmonics.audio.AudioPlayerManager
import me.mochibit.createharmonics.audio.process.ProcessLifecycleManager
import me.mochibit.createharmonics.audio.upload.AudioUploadServer
import me.mochibit.createharmonics.foundation.async.ClientCoroutineScope
import me.mochibit.createharmonics.foundation.async.ServerCoroutineScope
import me.mochibit.createharmonics.foundation.async.modLaunch
import me.mochibit.createharmonics.foundation.err
import me.mochibit.createharmonics.foundation.info
import me.mochibit.createharmonics.foundation.services.ConfigEvents
import me.mochibit.createharmonics.foundation.services.PlatformService
import me.mochibit.createharmonics.foundation.services.clientEventService
import me.mochibit.createharmonics.foundation.services.eventService
import me.mochibit.createharmonics.foundation.services.platformService
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.Registry

/**
 * Mod lifecycle handlers
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new ModLifecycleHandlers
 */
@AutoRegister
object ModLifecycleHandlers : Registrable {
    override fun register(registry: Registry<*>?) {
        // Client disconnects from a server (including leaving singleplayer/LAN)
        clientEventService.onClientDisconnect { controller, localPlayer, networkManager ->
            AudioPlayerManager.closeAll()
            modLaunch {
                ProcessLifecycleManager.shutdownAll()
                ClientCoroutineScope.reset()
            }
        }

        eventService.onLevelUnload { level ->
            if (level is ClientLevel) {
                AudioPlayerManager.closeAll()
                ClientCoroutineScope.reset()
            }
        }

        // Dedicated/integrated server stops

        eventService.onServerStarted {
            AudioUploadServer.start()
        }

        eventService.onServerStopped {
            ServerCoroutineScope.reset()
            AudioUploadServer.stop()
        }

        eventService.onConfigReload { modId, configType ->
            if (modId == MOD_ID && configType == ConfigEvents.ConfigType.SERVER) {
                modLaunch(Dispatchers.IO) {
                    try {
                        AudioUploadServer.onConfigReload()
                    } catch (e: Exception) {
                        "Unable to apply audio server config".err()
                        e.printStackTrace()
                    }
                }
            }
        }

        eventService.onGameShuttingDown {
            if (platformService isEnvironment PlatformService.Environment.CLIENT) {
                AudioPlayerManager.closeAll()
                ProcessLifecycleManager.shutdownAll()
                ClientCoroutineScope.reset()
            }
            ServerCoroutineScope.reset()
        }
    }
}
