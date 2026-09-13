package me.mochibit.createharmonics.foundation.registry

import me.mochibit.createharmonics.audio.AudioPlayerManager
import me.mochibit.createharmonics.audio.process.ProcessLifecycleManager
import me.mochibit.createharmonics.audio.upload.AudioUploadConfig
import me.mochibit.createharmonics.audio.upload.AudioUploadServer
import me.mochibit.createharmonics.foundation.async.ClientCoroutineScope
import me.mochibit.createharmonics.foundation.async.ServerCoroutineScope
import me.mochibit.createharmonics.foundation.async.modLaunch
import me.mochibit.createharmonics.foundation.info
import me.mochibit.createharmonics.foundation.services.PlatformService
import me.mochibit.createharmonics.foundation.services.clientEventService
import me.mochibit.createharmonics.foundation.services.eventService
import me.mochibit.createharmonics.foundation.services.platformService
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.Registry

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
            val config = AudioUploadConfig(
                port = 25566,
                storageRoot = platformService.serverRootPath.resolve("createharmonics/audio/uploaded"),
                maxFilesPerPlayer = 20,
                maxFileSizeBytes = 20L * 1024 * 1024,
            )
            val uploadServer = AudioUploadServer.get(config)
            uploadServer.start()
            "Started audio server, uploaded files will got to ${config.storageRoot}".info()
        }

        eventService.onServerStopped {
            ServerCoroutineScope.reset()
            val audioUploadServer = try {
                AudioUploadServer.get()
            } catch (e: Exception) { null }

            audioUploadServer?.stop()
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
