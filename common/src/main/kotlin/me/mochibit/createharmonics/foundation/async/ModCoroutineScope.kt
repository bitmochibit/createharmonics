package me.mochibit.createharmonics.foundation.async

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import me.mochibit.createharmonics.foundation.err
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine scope for tasks that should live as long as the game is running.
 * Only cancelled on game shutdown.
 */
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
     * Shutdown the coroutine scope completely. This should only be called on game unload.
     */
    fun shutdown() {
        supervisor.cancel()
    }
}

/**
 * Coroutine scope for client-side tasks.
 * Cancelled when the client disconnects from a server or the game shuts down.
 * A fresh scope is automatically created after cancellation for the next session.
 */
object ClientCoroutineScope {
    private val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            "Uncaught exception in client coroutine: $throwable".err()
            throwable.printStackTrace()
        }

    @Volatile
    private var supervisor = SupervisorJob()

    val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + supervisor + exceptionHandler

    fun launch(
        context: CoroutineContext = coroutineContext,
        block: suspend CoroutineScope.() -> Unit,
    ): Job = CoroutineScope(coroutineContext).launch(context, block = block)

    /**
     * Cancel all active client coroutines and recreate the supervisor for the next session.
     */
    fun cancelAll() {
        supervisor.cancelChildren()
    }

    /**
     * Shutdown completely. Only called on game exit.
     */
    fun shutdown() {
        supervisor.cancel()
        supervisor = SupervisorJob()
    }
}

/**
 * Coroutine scope for server-side tasks.
 * Cancelled when the server stops or the game shuts down.
 * A fresh scope is automatically created after cancellation for the next session.
 */
object ServerCoroutineScope {
    private val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            "Uncaught exception in server coroutine: $throwable".err()
            throwable.printStackTrace()
        }

    @Volatile
    private var supervisor = SupervisorJob()

    val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + supervisor + exceptionHandler

    fun launch(
        context: CoroutineContext = coroutineContext,
        block: suspend CoroutineScope.() -> Unit,
    ): Job = CoroutineScope(coroutineContext).launch(context, block = block)

    /**
     * Cancel all active server coroutines and recreate the supervisor for the next session.
     */
    fun cancelAll() {
        supervisor.cancelChildren()
    }

    /**
     * Shutdown completely. Only called on game exit.
     */
    fun shutdown() {
        supervisor.cancel()
        supervisor = SupervisorJob()
    }
}
