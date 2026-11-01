package me.mochibit.createharmonics.audio.upload

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import me.mochibit.createharmonics.config.ModConfigs
import me.mochibit.createharmonics.foundation.async.repeatingLaunch
import me.mochibit.createharmonics.foundation.info
import me.mochibit.createharmonics.foundation.network.packet.AudioServerInfoPacket
import me.mochibit.createharmonics.foundation.services.networkService
import me.mochibit.createharmonics.foundation.services.platformService
import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.io.path.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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

    private fun requireStorage(): AudioStorageManager =
        checkNotNull(storage) { "Audio server is not running" }

    fun hasFreeSlot(playerName: String): Boolean {
        val s = storage ?: return false
        return s.countFiles(playerName) < s.currentConfig().maxFilesPerPlayer
    }

    fun list(playerName: String) = storage?.list(playerName).orEmpty()
    fun delete(playerName: String, fileId: String) = storage?.delete(playerName, fileId)
    fun rename(playerName: String, fileId: String, newName: String?) =
        storage?.rename(playerName, fileId, newName)

    @Synchronized
    fun start() {
        check(running == null) { "AudioUploadServer already started" }

        val binding = configuredBinding()
        val http = HttpServer.create(InetSocketAddress(binding.host, binding.port), 0)
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

    @Synchronized
    fun stop(delaySeconds: Int = 1) {
        val r = running ?: return
        running = null
        r.cleaner.cancel()
        r.http.stop(delaySeconds)
        r.pool.shutdownNow()
        storage = null
        broadcastPort()
    }

    @Synchronized
    fun restart() {
        stop()
        start()
    }

    @Synchronized
    fun onConfigReload() {
        val r = running ?: return
        storage = buildStorage()
        if (configuredBinding() != r.binding) {
            "Audio server binding changed, restarting".info()
            restart()
        }
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