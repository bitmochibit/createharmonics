package me.mochibit.createharmonics.foundation.eventbus

import me.mochibit.createharmonics.foundation.registry.RegistryPhase
import me.mochibit.createharmonics.foundation.services.classScanningService
import me.mochibit.createharmonics.foundation.services.platformService
import net.minecraft.core.Registry

/**
 * Mod event
 *
 */
interface ModEvent

/**
 * Mix-in this interface to make an event cancellable.
 * Handlers that run at a lower priority will be skipped when [isCancelled] is true
 * (unless they register with [EventPriority.MONITOR] or set ignoreCancelled = false).
 *
 * @property isCancelled the is cancelled
 */
interface Cancellable {
    var isCancelled: Boolean
}

/**
 * Convenience base class providing a real [isCancelled] backing field.
 *
 * @constructor Creates a new CancellableEvent
 */
abstract class CancellableEvent : Cancellable {
    override var isCancelled: Boolean = false
}

/**
 * Determines the order in which handlers receive an event.
 * Handlers are invoked from [HIGHEST] down to [LOWEST], then [MONITOR].
 * [MONITOR] handlers always run last and always receive the event regardless of cancellation.
 * Use [MONITOR] only to observe the final state – never to modify the event.
 *
 * @constructor Creates a new EventPriority
 * @property order
 */
enum class EventPriority(
    val order: Int,
) {
    /**
     * Highest
     *
     * @constructor Creates a new HIGHEST
     */
    HIGHEST(0),

    /**
     * High
     *
     * @constructor Creates a new HIGH
     */
    HIGH(1),

    /**
     * Normal
     *
     * @constructor Creates a new NORMAL
     */
    NORMAL(2),

    /**
     * Low
     *
     * @constructor Creates a new LOW
     */
    LOW(3),

    /**
     * Lowest
     *
     * @constructor Creates a new LOWEST
     */
    LOWEST(4),

    /**
     * Monitor
     *
     * @constructor Creates a new MONITOR
     */
    MONITOR(5),
}

/**
 * Mod event handler
 *
 */
interface ModEventHandler {
    /**
     * Initialises the events
     *
     */
    fun setupEvents()
}

/**
 * Auto handler
 *
 * @constructor Creates a new AutoHandler
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AutoHandler

/**
 * Auto handler registrar
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new AutoHandlerRegistrar
 */
object AutoHandlerRegistrar {
    /**
     * Adds the all
     *
     */
    fun registerAll() {
        classScanningService.getClassesAnnotatedByWithData(AutoHandler::class.java)
            .mapNotNull { (clazz, _) -> resolveInstance(clazz) as? ModEventHandler }
            .forEach { it.setupEvents() }
    }

    private fun resolveInstance(clazz: Class<*>): Any? =
        runCatching { clazz.getField("INSTANCE").get(null) }
            .getOrElse {
                runCatching { clazz.getDeclaredConstructor().newInstance() }.getOrNull()
            }
}
