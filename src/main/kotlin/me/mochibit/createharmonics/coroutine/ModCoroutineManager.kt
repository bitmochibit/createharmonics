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
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

@Mod.EventBusSubscriber(modid = CreateHarmonicsMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
object ModCoroutineManager : CoroutineScope {
    private val supervisor = SupervisorJob()

    // Track world-specific jobs separately from global jobs
    private val worldJobs = ConcurrentHashMap<Job, Boolean>()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + supervisor + CoroutineExceptionHandler { _, throwable ->
            Logger.err("Error while executing coroutine! + $throwable")
        }

    @SubscribeEvent
    fun onServerStopping(event: ServerStoppingEvent) {
        Logger.info("Server stopping - cancelling all active coroutines...")
        cancelAllWorldCoroutines()
        supervisor.cancelChildren()
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    fun onClientDisconnect(event: ClientPlayerNetworkEvent.LoggingOut) {
        Logger.info("Client disconnecting from world - cancelling world coroutines...")
        cancelAllWorldCoroutines()
    }

    fun shutdown() {
        Logger.info("Shutting down Coroutine Manager...")
        cancelAllWorldCoroutines()
        supervisor.cancel()
    }

    /**
     * Cancel all world-specific coroutines.
     * This is called when leaving a world (back to main menu) or when server stops.
     */
    fun cancelAllWorldCoroutines() {
        val jobsToCancel = worldJobs.keys.toList()
        if (jobsToCancel.isNotEmpty()) {
            Logger.info("Cancelling ${jobsToCancel.size} world-specific coroutines...")
            jobsToCancel.forEach { job ->
                job.cancel()
                worldJobs.remove(job)
            }
        }
        // Also clear all audio streams
        StreamRegistry.clear()
    }

    /**
     * Register a job as world-specific so it can be cancelled when leaving the world.
     */
    internal fun registerWorldJob(job: Job) {
        worldJobs[job] = true
        // Clean up completed jobs
        job.invokeOnCompletion {
            worldJobs.remove(job)
        }
    }

    /**
     * Get the number of active world-specific jobs.
     * Useful for debugging memory leaks.
     */
    fun getActiveWorldJobCount(): Int = worldJobs.size
}

fun launchModCoroutine(
    context: CoroutineContext = MinecraftClientDispatcher,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    isWorldSpecific: Boolean = true,
    block: suspend CoroutineScope.() -> Unit
): Job {
    val job = ModCoroutineManager.launch(context, start, block)
    if (isWorldSpecific) {
        ModCoroutineManager.registerWorldJob(job)
    }
    return job
}

fun launchDelayed(
    context: CoroutineContext = MinecraftClientDispatcher,
    delay: Duration,
    isWorldSpecific: Boolean = true,
    block: suspend CoroutineScope.() -> Unit
): Job {
    val job = ModCoroutineManager.launch(context) {
        delay(delay.toMillis())
        block()
    }
    if (isWorldSpecific) {
        ModCoroutineManager.registerWorldJob(job)
    }
    return job
}

fun launchRepeating(
    context: CoroutineContext = MinecraftClientDispatcher,
    initialDelay: Duration = Duration.ZERO,
    delay: kotlin.time.Duration,
    isWorldSpecific: Boolean = true,
    block: suspend CoroutineScope.() -> Unit
): Job {
    val job = ModCoroutineManager.launch(context) {
        if (initialDelay.toMillis() > 0) delay(initialDelay.toMillis())
        while (isActive) {
            block()
            if (delay.inWholeMilliseconds > 0) delay(delay.inWholeMilliseconds)
        }
    }
    if (isWorldSpecific) {
        ModCoroutineManager.registerWorldJob(job)
    }
    return job
}

suspend fun <T> withServerContext(block: suspend CoroutineScope.() -> T): T {
    return withContext(MinecraftServerDispatcher, block)
}


suspend fun <T> withClientContext(block: suspend CoroutineScope.() -> T): T {
    return withContext(MinecraftClientDispatcher, block)
}

