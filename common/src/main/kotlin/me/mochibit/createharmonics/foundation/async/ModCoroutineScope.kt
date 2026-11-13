package me.mochibit.createharmonics.foundation.async

import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import me.mochibit.createharmonics.foundation.err
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine scope for tasks that should live as long as the game is running.
 * Only cancelled on game shutdown.
 *
 * @constructor Creates a new ModCoroutineScope
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
     * Creates a new child supervisor
     *
     * @return the completable job
     */
    internal fun createChildSupervisor(): CompletableJob = SupervisorJob(supervisor)
}

/**
 * Event bus scope
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new EventBusScope
 */
object EventBusScope : CoroutineScope {
    private val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            "Uncaught exception in EventBus handler: $throwable".err()
            throwable.printStackTrace()
        }

    override val coroutineContext: CoroutineContext =
        Dispatchers.Default + SupervisorJob() + exceptionHandler
}

/**
 * Coroutine scope for client-side tasks.
 * Cancelled when the client disconnects from a server or the game shuts down.
 * A fresh scope is automatically created after cancellation for the next session.
 *
 * @constructor Creates a new ClientCoroutineScope
 */
object ClientCoroutineScope : CoroutineScope {
    private val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            "Uncaught exception in client coroutine: $throwable".err()
            throwable.printStackTrace()
        }

    @Volatile
    private var supervisor =
        ModCoroutineScope.createChildSupervisor()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + supervisor + exceptionHandler

    /**
     * Stops the children
     *
     */
    fun cancelChildren() {
        coroutineContext.cancelChildren()
    }

    /**
     * Resets
     *
     */
    fun reset() {
        coroutineContext.cancel()
        supervisor = ModCoroutineScope.createChildSupervisor()
    }
}

/**
 * Coroutine scope for server-side tasks.
 * Cancelled when the server stops or the game shuts down.
 * A fresh scope is automatically created after cancellation for the next session.
 *
 * @constructor Creates a new ServerCoroutineScope
 */
object ServerCoroutineScope : CoroutineScope {
    private val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            "Uncaught exception in server coroutine: $throwable".err()
            throwable.printStackTrace()
        }

    @Volatile
    private var supervisor =
        ModCoroutineScope.createChildSupervisor()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + supervisor + exceptionHandler

    /**
     * Stops the children
     *
     */
    fun cancelChildren() {
        coroutineContext.cancelChildren()
    }

    /**
     * Resets
     *
     */
    fun reset() {
        coroutineContext.cancel()
        supervisor = ModCoroutineScope.createChildSupervisor()
    }
}
