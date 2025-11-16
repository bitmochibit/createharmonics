package me.mochibit.createharmonics.coroutine

import kotlinx.coroutines.*
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.audio.StreamRegistry
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.client.event.ClientPlayerNetworkEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.time.Duration
import kotlin.coroutines.CoroutineContext

/**
 * Manages coroutines for the mod with proper lifecycle handling.
 * All coroutines launched through this manager will be cancelled when the world is unloaded or server stops.
 */
@Mod.EventBusSubscriber(modid = CreateHarmonicsMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
object ModCoroutineManager : CoroutineScope {
    private val supervisor = SupervisorJob()
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Logger.err("Uncaught exception in coroutine: $throwable")
        throwable.printStackTrace()
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + supervisor + exceptionHandler

    @SubscribeEvent
    fun onServerStopping(event: ServerStoppingEvent) {
        Logger.info("Server stopping - cancelling all coroutines...")
        cancelAll()
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    fun onClientDisconnect(event: ClientPlayerNetworkEvent.LoggingOut) {
        Logger.info("Client disconnecting - cancelling all coroutines...")
        cancelAll()
    }

    /**
     * Cancel all active coroutines and clean up resources.
     */
    fun cancelAll() {
        supervisor.cancelChildren()
        StreamRegistry.clear()
    }

    /**
     * Shutdown the coroutine manager completely.
     * This should only be called on mod unload.
     */
    fun shutdown() {
        Logger.info("Shutting down coroutine manager...")
        supervisor.cancel()
        StreamRegistry.clear()
    }
}


/**
 * Launch a coroutine in the mod's coroutine scope.
 * The coroutine will be automatically cancelled when the world is unloaded.
 *
 * @param context The coroutine context to use (defaults to MinecraftClientDispatcher)
 * @param start The coroutine start mode
 * @param block The coroutine code block
 * @return The launched Job
 */
fun launchModCoroutine(
    context: CoroutineContext = MinecraftClientDispatcher,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
): Job = ModCoroutineManager.launch(context, start, block)

/**
 * Launch a coroutine with an initial delay.
 *
 * @param context The coroutine context to use
 * @param delay The initial delay before execution
 * @param block The coroutine code block
 * @return The launched Job
 */
fun launchDelayed(
    context: CoroutineContext = MinecraftClientDispatcher,
    delay: Duration,
    block: suspend CoroutineScope.() -> Unit
): Job = ModCoroutineManager.launch(context) {
    delay(delay.toMillis())
    block()
}

/**
 * Launch a repeating coroutine that executes periodically.
 *
 * @param context The coroutine context to use
 * @param initialDelay Delay before the first execution
 * @param delay Delay between subsequent executions
 * @param block The coroutine code block to repeat
 * @return The launched Job
 */
fun launchRepeating(
    context: CoroutineContext = MinecraftClientDispatcher,
    initialDelay: Duration = Duration.ZERO,
    delay: kotlin.time.Duration,
    block: suspend CoroutineScope.() -> Unit
): Job = ModCoroutineManager.launch(context) {
    if (initialDelay.toMillis() > 0) {
        delay(initialDelay.toMillis())
    }
    while (isActive) {
        block()
        if (delay.inWholeMilliseconds > 0) {
            delay(delay.inWholeMilliseconds)
        }
    }
}

/**
 * Execute a suspend block in the server dispatcher context.
 */
suspend fun <T> withServerContext(block: suspend CoroutineScope.() -> T): T =
    withContext(MinecraftServerDispatcher, block)

/**
 * Execute a suspend block in the client dispatcher context.
 */
suspend fun <T> withClientContext(block: suspend CoroutineScope.() -> T): T =
    withContext(MinecraftClientDispatcher, block)
