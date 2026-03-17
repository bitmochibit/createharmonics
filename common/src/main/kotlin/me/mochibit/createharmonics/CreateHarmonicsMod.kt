package me.mochibit.createharmonics

import me.mochibit.createharmonics.audio.process.ProcessLifecycleManager
import me.mochibit.createharmonics.foundation.async.ModCoroutineScope
import me.mochibit.createharmonics.foundation.err
import me.mochibit.createharmonics.foundation.eventbus.autoHandler
import me.mochibit.createharmonics.foundation.registry.CommonRegistry
import me.mochibit.createharmonics.foundation.registry.autoRegister
import me.mochibit.createharmonics.gui.CommonGuiEventHandler

object CreateHarmonicsMod {
    const val MOD_ID = "createharmonics"
    private var initialized = false

    fun commonSetup() {
        if (initialized) {
            return "Common was already initialized".err()
        }
        initialized = true

        autoRegister<CommonRegistry>()
        autoHandler<CommonGuiEventHandler>()

        Runtime.getRuntime().addShutdownHook(
            Thread {
                try {
                    ProcessLifecycleManager.shutdownAll()
                    ModCoroutineScope.shutdown()
                } catch (e: Exception) {
                    "Error shutting down processes: ${e.message}".err()
                }
            },
        )
    }
}
