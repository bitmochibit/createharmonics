package me.mochibit.createharmonics.foundation.eventbus

interface ModEvent

/**
 * Mix-in this interface to make an event cancellable.
 * Handlers that run at a lower priority will be skipped when [isCancelled] is true
 * (unless they register with [EventPriority.MONITOR] or set ignoreCancelled = false).
 */
interface Cancellable : ModEvent {
    var isCancelled: Boolean
}

/** Convenience base class providing a real [isCancelled] backing field. */
abstract class CancellableEvent : Cancellable {
    override var isCancelled: Boolean = false
}

/**
 * Determines the order in which handlers receive an event.
 * Handlers are invoked from [HIGHEST] down to [LOWEST], then [MONITOR].
 * [MONITOR] handlers always run last and always receive the event regardless of cancellation.
 * Use [MONITOR] only to observe the final state – never to modify the event.
 */
enum class EventPriority(
    val order: Int,
) {
    HIGHEST(0),
    HIGH(1),
    NORMAL(2),
    LOW(3),
    LOWEST(4),
    MONITOR(5),
}
