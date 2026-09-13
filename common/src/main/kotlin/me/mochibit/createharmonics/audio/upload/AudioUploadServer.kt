package me.mochibit.createharmonics.audio.upload

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors


class AudioUploadServer(private val config: AudioUploadConfig) {

    companion object {
        @Volatile
        private var instance: AudioUploadServer? = null

        fun get(config: AudioUploadConfig): AudioUploadServer {
            return instance ?: synchronized(this) {
                instance ?: AudioUploadServer(config).also { instance = it }
            }
        }

        fun get(): AudioUploadServer {
            return checkNotNull(instance) {
                "AudioUploadServer is not initialized. Call getInstance(config) first."
            }
        }
    }

    private val storage = AudioStorageManager(config)
    private var server: HttpServer? = null

    @Synchronized
    fun start() {
        check(server == null) { "AudioUploadServer already started" }

        val httpServer = HttpServer.create(InetSocketAddress(config.bindHost, config.port), 0)
        httpServer.createContext("/audio/upload", UploadHandler(config, storage))
        httpServer.createContext("/audio/stream/", StreamHandler(config, storage))
        httpServer.executor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "createharmonics-audio-http").apply { isDaemon = true }
        }
        httpServer.start()
        server = httpServer
    }

    @Synchronized
    fun stop(delaySeconds: Int = 1) {
        server?.stop(delaySeconds)
        server = null
    }
}