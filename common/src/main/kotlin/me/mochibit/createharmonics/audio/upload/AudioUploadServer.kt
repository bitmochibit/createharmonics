package me.mochibit.createharmonics.audio.upload

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import me.mochibit.createharmonics.config.ModConfigs
import me.mochibit.createharmonics.foundation.async.repeatingLaunch
import me.mochibit.createharmonics.foundation.info
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import kotlin.io.path.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


object AudioUploadServer {

    private var storage: AudioStorageManager = buildStorage()
    private var server: HttpServer? = null

    val isRunning get() = server != null
    fun hasFreeSlot(playerName: String) =
        storage.countFiles(playerName) < storage.currentConfig().maxFilesPerPlayer

    private var ticketCleaner: Job? = null

    @Synchronized
    fun start() {
        check(server == null) { "AudioUploadServer already started" }

        val host = ModConfigs.server.audioServerIp.get()
        val port = ModConfigs.server.audioServerPort.get()

        val httpServer = HttpServer.create(InetSocketAddress(host, port), 0)
        httpServer.createContext("/audio/upload", UploadHandler(storage))
        httpServer.createContext("/audio/stream/", StreamHandler(storage))
        httpServer.executor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "createharmonics-audio-http").apply { isDaemon = true }
        }
        httpServer.start()
        server = httpServer

        ticketCleaner = repeatingLaunch(Dispatchers.IO, Duration.ZERO, 20.seconds) {
            UploadTicketRegistry.purgeExpired()
        }

        val currentStorageConfiguration = this.storage.currentConfig()
        ("Started audio hosting server on ${host}:${port} \n" +
                "Make sure that port is forwarded correctly \n" +
                "Players files will go in ${currentStorageConfiguration.storageRoot}, max ${currentStorageConfiguration.maxFilesPerPlayer} files (${currentStorageConfiguration.maxFileSizeBytes / 1024 / 1024} MB) for each player").info()
    }

    @Synchronized
    fun stop(delaySeconds: Int = 1) {
        server?.stop(delaySeconds)

        ticketCleaner?.cancel()
        ticketCleaner = null

        server = null
    }

    private fun buildStorage(): AudioStorageManager {
        return AudioStorageManager(
            storageRoot = Path(ModConfigs.server.audioStorageRoot.get()),
            maxFilesPerPlayer = ModConfigs.server.maxFilesPerPlayer.get(),
            maxFileSizeBytes = ModConfigs.server.maxFileSizeBytes.get()
        )
    }

    @Synchronized
    fun updateStorageConfig() {
        this.storage = buildStorage()
    }
}