package me.mochibit.createharmonics.foundation.async

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.mochibit.createharmonics.foundation.services.PlatformService
import me.mochibit.createharmonics.foundation.services.platformService
import kotlin.coroutines.CoroutineContext

val currentMainDispatcher: CoroutineContext
    get() =
        if (platformService.currentThreadSide ==
            PlatformService.Environment.SERVER
        ) {
            ModDispatchers.Server()
        } else {
            ModDispatchers.Client()
        }

private fun currentScope(): CoroutineScope =
    if (platformService isEnvironment
        PlatformService.Environment.CLIENT
    ) {
        ClientCoroutineScope
    } else {
        ServerCoroutineScope
    }

/**
 * On client thread
 * Suspending function — must be called from a coroutine or another suspending function.
 *
 * @param block
 * @receiver
 */
suspend fun onClientThread(block: suspend CoroutineScope.() -> Unit) = withContext(ModDispatchers.Client(), block)

/**
 * On server thread
 * Suspending function — must be called from a coroutine or another suspending function.
 *
 * @param block
 * @receiver
 */
suspend fun onServerThread(block: suspend CoroutineScope.() -> Unit) = withContext(ModDispatchers.Server(), block)

/**
 * Starts the on client
 *
 * @param block
 * @receiver
 * @return the job
 */
fun launchOnClient(block: suspend CoroutineScope.() -> Unit) = ClientCoroutineScope.launch(ModDispatchers.Client(), block = block)

/**
 * Starts the on server
 *
 * @param block
 * @receiver
 * @return the job
 */
fun launchOnServer(block: suspend CoroutineScope.() -> Unit) = ServerCoroutineScope.launch(ModDispatchers.Server(), block = block)

/**
 * Mod launch
 *
 * @param context
 * @param block
 * @receiver
 * @return the job
 */
fun modLaunch(
    context: CoroutineContext = Dispatchers.Default,
    block: suspend CoroutineScope.() -> Unit,
) = currentScope().launch(context, block = block)

/**
 * Delayed launch
 *
 * @param context
 * @param delay
 * @param block
 * @receiver
 * @return the job
 */
fun delayedLaunch(
    context: CoroutineContext = Dispatchers.Default,
    delay: kotlin.time.Duration,
    block: suspend CoroutineScope.() -> Unit,
) = currentScope().launch(context) {
    delay(delay.inWholeMilliseconds)
    block()
}

/**
 * Then launch on the receiver [kotlin.time.Duration]
 *
 * @param block
 * @receiver
 * @return the job
 */
infix fun kotlin.time.Duration.thenLaunch(block: suspend CoroutineScope.() -> Unit) = delayedLaunch(delay = this, block = block)

/**
 * Repeating launch
 *
 * @param context
 * @param initialDelay the initial delay
 * @param delay
 * @param block
 * @receiver
 * @return the job
 */
fun repeatingLaunch(
    context: CoroutineContext = Dispatchers.Default,
    initialDelay: kotlin.time.Duration = kotlin.time.Duration.ZERO,
    delay: kotlin.time.Duration,
    block: suspend CoroutineScope.() -> Unit,
) = currentScope().launch(context) {
    if (initialDelay.inWholeMilliseconds > 0) {
        delay(initialDelay.inWholeMilliseconds)
    }
    while (isActive) {
        block()
        if (delay.inWholeMilliseconds > 0) {
            delay(delay.inWholeMilliseconds)
        }
    }
}

/**
 * Every on the receiver [kotlin.time.Duration]
 *
 * @param block
 * @receiver
 * @return the job
 */
infix fun kotlin.time.Duration.every(block: suspend CoroutineScope.() -> Unit) = repeatingLaunch(delay = this, block = block)

/**
 * With main context
 * Suspending function — must be called from a coroutine or another suspending function.
 *
 * @param T
 * @param block
 * @receiver
 * @return the t
 */
suspend fun <T> withMainContext(block: suspend CoroutineScope.() -> T): T =
    withContext(
        currentMainDispatcher,
        block,
    )
