package me.mochibit.createharmonics.audio.upload

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import me.mochibit.createharmonics.config.ModConfigs
import me.mochibit.createharmonics.foundation.async.repeatingLaunch
import me.mochibit.createharmonics.foundation.info
import me.mochibit.createharmonics.foundation.network.packet.AudioServerInfoPacket
import me.mochibit.createharmonics.foundation.services.networkService
import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.io.path.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object AudioUploadServer {

    @Volatile
    private var storage: AudioStorageManager = buildStorage()

    @Volatile
    private var server: HttpServer? = null

    private var executor: ExecutorService? = null
    private var ticketCleaner: Job? = null

    /** Port the audio server is currently listening on, or 0 if it is not running. */
    @Volatile
    var port: Int = 0
        private set

    @Volatile
    var host: String = "0.0.0.0"
        private set

    val isRunning get() = server != null

    fun hasFreeSlot(playerName: String) =
        storage.countFiles(playerName) < storage.currentConfig().maxFilesPerPlayer

    fun list(playerName: String) = storage.list(playerName)

    fun delete(playerName: String, fileId: String) = storage.delete(playerName, fileId)

    @Synchronized
    fun start() {
        check(server == null) { "AudioUploadServer already started" }

        storage = buildStorage()
        val boundHost = ModConfigs.server.audioServerIp.get()
        val boundPort = ModConfigs.server.audioServerPort.get()

        val httpServer = HttpServer.create(InetSocketAddress(boundHost, boundPort), 0)
        val pool = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "createharmonics-audio-http").apply { isDaemon = true }
        }

        try {
            httpServer.createContext("/audio/upload", UploadHandler { storage })
            httpServer.createContext("/audio/stream/", StreamHandler { storage })
            httpServer.executor = pool
            httpServer.start()
        } catch (e: Exception) {
            pool.shutdownNow()
            httpServer.stop(0)
            throw e
        }

        server = httpServer
        executor = pool
        port = boundPort
        host = boundHost

        ticketCleaner = repeatingLaunch(Dispatchers.IO, Duration.ZERO, 20.seconds) {
            UploadTicketRegistry.purgeExpired()
        }

        broadcastPort()

        val config = storage.currentConfig()
        "Started audio hosting server on $host:$boundPort".info()
        "Make sure that port is forwarded correctly".info()
        "Players files will go in ${config.storageRoot}, max ${config.maxFilesPerPlayer} files (${config.maxFileSizeBytes / 1024 / 1024} MB) for each player".info()
    }

    @Synchronized
    fun stop(delaySeconds: Int = 1) {
        server?.stop(delaySeconds)
        executor?.shutdown()
        ticketCleaner?.cancel()

        server = null
        executor = null
        ticketCleaner = null
        port = 0

        broadcastPort()
    }

    @Synchronized
    fun updateStorageConfig() {
        storage = buildStorage()
    }

    /**
     * Sends the current port (0 = unavailable) to every connected player.
     * Hops onto the server thread since this can be called from config/reload threads.
     */
    private fun broadcastPort() {
        networkService.broadcast(AudioServerInfoPacket(port, host))
    }

    private fun buildStorage() = AudioStorageManager(
        storageRoot = Path(ModConfigs.server.audioStorageRoot.get()),
        maxFilesPerPlayer = ModConfigs.server.maxFilesPerPlayer.get(),
        maxFileSizeBytes = ModConfigs.server.maxFileSizeBytes.get()
    )
}