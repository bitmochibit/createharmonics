package me.mochibit.createharmonics.foundation.async

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import me.mochibit.createharmonics.foundation.err
import me.mochibit.createharmonics.foundation.warn
import kotlin.coroutines.CoroutineContext

object ModCoroutineScope : CoroutineScope {
    private val supervisor = SupervisorJob()
    private val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            "Uncaught exception in coroutine: $throwable".err()
            throwable.printStackTrace()
        }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + supervisor + exceptionHandler

    /**
     * Cancel all active coroutines, but keep the scope alive for future launches.
     * This should be used if changing worlds or disconnecting from a server.
     */
    fun cancelAll() {
        "Cancelling all active coroutines".warn()
        supervisor.cancelChildren()
    }

    /**
     * Shutdown the coroutine scope completely. This should only be called on mod unload.
     */
    fun shutdown() {
        "Shutting down coroutine scope...".warn()
        supervisor.cancel()
    }
}
