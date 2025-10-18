package me.mochibit.createharmonics.coroutine

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.time.Duration
import kotlin.coroutines.CoroutineContext

@Mod.EventBusSubscriber(modid = CreateHarmonicsMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
object ModCoroutineManager : CoroutineScope {
    private val supervisor = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + supervisor + CoroutineExceptionHandler { _, throwable ->
            Logger.err("Error while executing coroutine! + $throwable")
        }

    @SubscribeEvent
    fun onServerStopping(event: ServerStoppingEvent) {
        Logger.info("Cancelling all active coroutines...")
        supervisor.cancelChildren()
    }

    fun shutdown() {
        Logger.info("Shutting down Coroutine Manager...")
        supervisor.cancel()
    }
}

fun launchModCoroutine(
    context: CoroutineContext = MinecraftClientDispatcher,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
): Job {
    return ModCoroutineManager.launch(context, start, block)
}

fun launchDelayed(
    context: CoroutineContext = MinecraftClientDispatcher,
    delay: Duration,
    block: suspend CoroutineScope.() -> Unit
): Job {
    return ModCoroutineManager.launch(context) {
        delay(delay.toMillis())
        block()
    }
}

fun launchRepeating(
    context: CoroutineContext = MinecraftClientDispatcher,
    initialDelay: Duration = Duration.ZERO,
    delay: Duration,
    block: suspend CoroutineScope.() -> Unit
): Job {
    return ModCoroutineManager.launch(context) {
        if (initialDelay.toMillis() > 0) delay(initialDelay.toMillis())
        while (isActive) {
            block()
            if (delay.toMillis() > 0) delay(delay.toMillis())
        }
    }
}

suspend fun <T> withServerContext(block: suspend CoroutineScope.() -> T): T {
    return withContext(MinecraftServerDispatcher, block)
}


suspend fun <T> withClientContext(block: suspend CoroutineScope.() -> T): T {
    return withContext(MinecraftClientDispatcher, block)
}

