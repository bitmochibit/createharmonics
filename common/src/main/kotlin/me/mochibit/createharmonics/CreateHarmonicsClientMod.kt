package me.mochibit.createharmonics

import kotlinx.coroutines.runBlocking
import me.mochibit.createharmonics.audio.bin.BinStatusManager
import me.mochibit.createharmonics.audio.process.ProcessLifecycleManager
import me.mochibit.createharmonics.foundation.async.launchOnClient
import me.mochibit.createharmonics.foundation.err

object CreateHarmonicsClientMod {
    private var initialized = false

    fun setup() {
        if (initialized) {
            return "Client side was already initialized".err()
        }
        initialized = true

        launchOnClient {
            BinStatusManager.initialize()
        }

        Runtime.getRuntime().addShutdownHook(
            Thread {
                try {
                    runBlocking {
                        ProcessLifecycleManager.shutdownAll()
                    }
                } catch (e: Exception) {
                    "Error shutting down processes: ${e.message}".err()
                }
            },
        )
    }
}
