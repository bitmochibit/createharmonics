package me.mochibit.createharmonics.audio.upload

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import me.mochibit.createharmonics.config.ModConfigs
import me.mochibit.createharmonics.foundation.async.modLaunch
import me.mochibit.createharmonics.foundation.async.repeatingLaunch
import me.mochibit.createharmonics.foundation.err
import me.mochibit.createharmonics.foundation.info
import me.mochibit.createharmonics.foundation.network.packet.AudioServerInfoPacket
import me.mochibit.createharmonics.foundation.services.networkService
import me.mochibit.createharmonics.foundation.services.platformService
import java.net.BindException
import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Audio upload server
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new AudioUploadServer
 */
object AudioUploadServer {

    private data class Binding(val host: String, val port: Int)

    private class Running(
        val http: HttpServer,
        val pool: ExecutorService,
        val cleaner: Job,
        val binding: Binding,
    )

    @Volatile private var running: Running? = null
    @Volatile private var storage: AudioStorageManager? = null

    val isRunning get() = running != null
    val port: Int get() = running?.binding?.port ?: 0
    val host: String get() = running?.binding?.host ?: ""

    @Volatile private var starting: Job? = null


    private fun requireStorage(): AudioStorageManager =
        checkNotNull(storage) { "Audio server is not running" }

    /**
     * Returns `true` if free slot
     *
     * @param playerName the player name
     * @return `true` if the condition holds, `false` otherwise
     */
    fun hasFreeSlot(playerName: String): Boolean {
        val s = storage ?: return false
        return s.countFiles(playerName) < s.currentConfig().maxFilesPerPlayer
    }

    /**
     * List
     *
     * @param playerName the player name
     * @return the audio metadatas
     */
    fun list(playerName: String) = storage?.list(playerName).orEmpty()

    /**
     * Removes
     *
     * @param playerName the player name
     * @param fileId the file id
     * @return `true` if the condition holds, `false` otherwise
     */
    fun delete(playerName: String, fileId: String) = storage?.delete(playerName, fileId)

    /**
     * Rename
     *
     * @param playerName the player name
     * @param fileId the file id
     * @param newName the new name
     * @return the audio metadata
     */
    fun rename(playerName: String, fileId: String, newName: String?) =
        storage?.rename(playerName, fileId, newName)


    /**
     * Starts
     *
     * @throws e
     */
    fun start() {
        synchronized(this) {
            if (running != null || starting?.isActive == true) return
            starting = modLaunch(Dispatchers.IO) {
                try {
                    bindWithRetry()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    "Audio server failed to start: ${e.message}".err()
                }
            }
        }
    }

    private suspend fun bindWithRetry(attempts: Int = 25, retryDelay: Duration = 200.milliseconds) {
        val binding = configuredBinding()
        var last: BindException? = null
        repeat(attempts) {
            try {
                val http = HttpServer.create(InetSocketAddress(binding.host, binding.port), 0)
                install(http, binding)
                return
            } catch (e: BindException) {
                last = e
                delay(retryDelay)
            }
        }
        throw checkNotNull(last)
    }

    private fun install(http: HttpServer, binding: Binding) = synchronized(this) {
        if (starting?.isActive != true) {
            http.stop(0)
            return
        }
        val pool = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("createharmonics-audio-http-", 0).factory()
        )
        storage = buildStorage()
        try {
            http.createContext("/audio/upload", UploadHandler(::requireStorage))
            http.createContext("/audio/stream/", StreamHandler(::requireStorage))
            http.createContext("/audio/info/", InfoHandler(::requireStorage))
            http.executor = pool
            http.start()
        } catch (e: Exception) {
            pool.shutdownNow()
            http.stop(0)
            storage = null
            throw e
        }

        val cleaner = repeatingLaunch(Dispatchers.IO, Duration.ZERO, 20.seconds) {
            UploadTicketRegistry.purgeExpired()
        }
        running = Running(http, pool, cleaner, binding)
        broadcastPort()

        val config = requireStorage().currentConfig()
        "Started audio hosting server on ${binding.host}:${binding.port}".info()
        "Make sure that port is forwarded correctly".info()
        "Players files will go in ${config.storageRoot}, max ${config.maxFilesPerPlayer} files (${config.maxFileSizeBytes / 1024 / 1024} MB) for each player".info()
    }


    /**
     * Stops
     *
     * @param delaySeconds the delay seconds
     */
    @Synchronized
    fun stop(delaySeconds: Int = 1) {
        starting?.cancel()
        starting = null
        val r = running ?: return
        running = null
        r.cleaner.cancel()
        r.http.stop(delaySeconds)
        r.pool.shutdownNow()
        storage = null
        broadcastPort()
    }

    /**
     * Restart
     *
     */
    @Synchronized
    fun restart() {
        stop()
        start()
    }

    /**
     * On config reload
     *
     */
    @Synchronized
    fun onConfigReload() {
        val r = running ?: return
        storage = buildStorage()
        if (configuredBinding() != r.binding) {
            "Audio server binding changed, restarting".info()
            restart()
        }
    }

    private fun createServer(binding: Binding, attempts: Int = 15): HttpServer {
        var last: java.net.BindException? = null
        repeat(attempts) {
            try {
                return HttpServer.create(InetSocketAddress(binding.host, binding.port), 0)
            } catch (e: java.net.BindException) {
                last = e
                Thread.sleep(200)
            }
        }
        throw checkNotNull(last)
    }

    private fun configuredBinding() = Binding(
        ModConfigs.server.audioServerIp.get().ifBlank { "0.0.0.0" },
        ModConfigs.server.audioServerPort.get(),
    )

    private fun broadcastPort() {
        networkService.broadcast(AudioServerInfoPacket(port, host))
    }

    private fun buildStorage() = AudioStorageManager(
        storageRoot = platformService.serverRootPath.resolve(ModConfigs.server.audioStorageRoot.get()),
        maxFilesPerPlayer = ModConfigs.server.maxFilesPerPlayer.get(),
        maxFileSizeBytes = ModConfigs.server.maxFileSizeBytes.get(),
    )
}