package me.mochibit.createharmonics.foundation.async

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.mochibit.createharmonics.foundation.services.PlatformService
import me.mochibit.createharmonics.foundation.services.platformService
import kotlin.coroutines.CoroutineContext

private val isClient: Boolean
    get() = platformService isEnvironment PlatformService.Environment.CLIENT

val currentMainDispatcher: CoroutineContext
    get() = if (isClient) ModDispatchers.Client() else ModDispatchers.Server()

private fun currentScope(): CoroutineScope =
    if (isClient) {
        CoroutineScope(ClientCoroutineScope.coroutineContext)
    } else {
        CoroutineScope(ServerCoroutineScope.coroutineContext)
    }

fun modLaunch(
    context: CoroutineContext = currentMainDispatcher,
    block: suspend CoroutineScope.() -> Unit,
) = currentScope().launch(context, block = block)

fun delayedLaunch(
    context: CoroutineContext = currentMainDispatcher,
    delay: kotlin.time.Duration,
    block: suspend CoroutineScope.() -> Unit,
) = currentScope().launch(context) {
    delay(delay.inWholeMilliseconds)
    block()
}

infix fun kotlin.time.Duration.thenLaunch(block: suspend CoroutineScope.() -> Unit) = delayedLaunch(delay = this, block = block)

fun repeatingLaunch(
    context: CoroutineContext = currentMainDispatcher,
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

infix fun kotlin.time.Duration.every(block: suspend CoroutineScope.() -> Unit) = repeatingLaunch(delay = this, block = block)

suspend fun <T> withMainContext(block: suspend CoroutineScope.() -> T): T =
    withContext(
        currentMainDispatcher,
        block,
    )
