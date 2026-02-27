package me.mochibit.createharmonics

import me.mochibit.createharmonics.audio.AudioPlayerRegistry
import me.mochibit.createharmonics.audio.process.ProcessLifecycleManager
import me.mochibit.createharmonics.foundation.err

object CreateHarmonicsMod {
    const val MOD_ID = "createharmonics"
    private var initialized = false

    fun commonSetup() {
        if (initialized) {
            return "CreateHarmonicsMod is already initialized.".err()
        }
        initialized = true

        Runtime.getRuntime().addShutdownHook(
            Thread {
                try {
                    AudioPlayerRegistry.clear()
                    ProcessLifecycleManager.shutdownAll()
                } catch (e: Exception) {
                    "Error shutting down processes: ${e.message}".err()
                }
            },
        )
    }
}
