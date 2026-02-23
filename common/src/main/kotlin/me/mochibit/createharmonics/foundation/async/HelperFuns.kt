package me.mochibit.createharmonics.foundation.async

import dev.architectury.event.Event
import dev.architectury.platform.Platform
import dev.architectury.utils.Env
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.mochibit.createharmonics.foundation.warn
import net.minecraftforge.event.server.ServerStoppingEvent
import java.time.Duration
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.minutes

fun modLaunch(
    context: CoroutineContext = if (Platform.getEnvironment() == Env.CLIENT) ModDispatchers.Client() else ModDispatchers.Server(),
    block: suspend CoroutineScope.() -> Unit,
) = ModCoroutineScope.launch(context, block = block)

fun delayedLaunch(
    context: CoroutineContext = if (Platform.getEnvironment() == Env.CLIENT) ModDispatchers.Client() else ModDispatchers.Server(),
    delay: kotlin.time.Duration,
    block: suspend CoroutineScope.() -> Unit,
) = ModCoroutineScope.launch(context) {
    delay(delay.inWholeMilliseconds)
    block()
}

infix fun kotlin.time.Duration.thenLaunch(block: suspend CoroutineScope.() -> Unit) = delayedLaunch(delay = this, block = block)

infix fun (suspend CoroutineScope.() -> Unit).after(delay: kotlin.time.Duration) = delayedLaunch(delay = delay, block = this)

fun repeatingLaunch(
    context: CoroutineContext = if (Platform.getEnvironment() == Env.CLIENT) ModDispatchers.Client() else ModDispatchers.Server(),
    initialDelay: kotlin.time.Duration = kotlin.time.Duration.ZERO,
    delay: kotlin.time.Duration,
    block: suspend CoroutineScope.() -> Unit,
) = ModCoroutineScope.launch(context) {
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

infix fun (suspend CoroutineScope.() -> Unit).every(delay: kotlin.time.Duration) = repeatingLaunch(delay = delay, block = this)

suspend fun <T> withMainContext(block: suspend CoroutineScope.() -> T): T =
    withContext(
        if (Platform.getEnvironment() == Env.CLIENT) ModDispatchers.Client() else ModDispatchers.Server(),
        block,
    )
