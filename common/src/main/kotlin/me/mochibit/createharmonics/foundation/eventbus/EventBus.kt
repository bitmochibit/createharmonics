package me.mochibit.createharmonics.foundation.eventbus

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import me.mochibit.createharmonics.foundation.async.ModCoroutineScope
import kotlin.reflect.KClass

object EventBus {
    /**
     * One [MutableSharedFlow] per priority tier, looked up by [EventPriority.ordinal].
     * Events are emitted into every tier's flow in [EventPriority] order so that
     * higher-priority handlers always run (and can cancel the event) before lower-priority ones.
     */
    @PublishedApi
    internal val tierFlows: Map<EventPriority, MutableSharedFlow<ModEvent>> =
        EventPriority.entries.associateWith { MutableSharedFlow(extraBufferCapacity = 64) }

    /**
     * Post [event] fire-and-forget across all priority tiers.
     * Use for non-cancellable events where you don't need to observe side-effects.
     */
    fun post(event: ModEvent) {
        EventPriority.entries.forEach { priority ->
            tierFlows[priority]!!.tryEmit(event)
        }
    }

    /**
     * Post [event] and suspend until each priority tier has processed it in order.
     * Cancellable events will have [Cancellable.isCancelled] set correctly by the time
     * this function returns, so callers can check it immediately afterwards.
     */
    suspend fun postAndAwait(event: ModEvent) {
        EventPriority.entries.forEach { priority ->
            tierFlows[priority]!!.emit(event)
        }
    }

    /**
     * Subscribe to events of type [T].
     *
     * @param priority        Controls when this handler runs relative to others. Defaults to [EventPriority.NORMAL].
     * @param listenSubclasses If `true`, also matches subtype instances of [T].
     * @param ignoreCancelled  If `true` (default), the handler is skipped when [Cancellable.isCancelled] is already `true`.
     *                         [EventPriority.MONITOR] handlers always run regardless of this flag.
     * @return A [Job] — cancel it to unsubscribe.
     */
    inline fun <reified T : ModEvent> on(
        priority: EventPriority = EventPriority.NORMAL,
        listenSubclasses: Boolean = false,
        ignoreCancelled: Boolean = true,
        noinline handler: suspend (T) -> Unit,
    ): Job = on(T::class, priority, listenSubclasses, ignoreCancelled, handler)

    @PublishedApi
    internal fun <T : ModEvent> on(
        klass: KClass<T>,
        priority: EventPriority,
        listenSubclasses: Boolean,
        ignoreCancelled: Boolean,
        handler: suspend (T) -> Unit,
    ): Job {
        val shouldRun = { event: ModEvent ->
            val typeMatch = if (listenSubclasses) klass.isInstance(event) else event::class == klass
            val cancellationOk =
                priority == EventPriority.MONITOR ||
                    !ignoreCancelled ||
                    (event !is Cancellable || !event.isCancelled)
            typeMatch && cancellationOk
        }

        return tierFlows[priority]!!
            .filter { shouldRun(it) }
            .map {
                @Suppress("UNCHECKED_CAST")
                it as T
            }.onEach { handler(it) }
            .launchIn(ModCoroutineScope)
    }

    /**
     * Suspend until the next event of type [T] is posted, then return it.
     *
     * @param listenSubclasses If `true`, also matches subtype instances.
     * @param priority         Which tier to listen on. Defaults to [EventPriority.MONITOR] so the event
     *                         is observed in its final state (after all other handlers have run).
     */
    suspend inline fun <reified T : ModEvent> awaitFirst(
        listenSubclasses: Boolean = false,
        priority: EventPriority = EventPriority.MONITOR,
    ): T =
        tierFlows[priority]!!
            .filter { if (listenSubclasses) it is T else it::class == T::class }
            .map {
                @Suppress("UNCHECKED_CAST")
                it as T
            }.first()
}
