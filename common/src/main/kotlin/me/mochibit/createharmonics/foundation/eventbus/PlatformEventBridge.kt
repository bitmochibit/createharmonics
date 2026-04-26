
package me.mochibit.createharmonics.foundation.eventbus

import kotlin.reflect.KClass

abstract class PlatformEventBridge<PE : Any> {
    abstract fun <FE : PE> registerListener(
        klass: KClass<FE>,
        mapper: FE.() -> ServerProxyEvent,
    )

    open fun <FE : PE> registerClientListener(
        klass: KClass<FE>,
        mapper: FE.() -> ClientProxyEvent,
    ) {}

    open fun onServerRegistered(klass: KClass<out ServerProxyEvent>) {}

    open fun onClientRegistered(klass: KClass<out ClientProxyEvent>) {}

    inner class ProxyBuilder<FE : PE>(
        val klass: KClass<FE>,
    ) {
        inline fun <reified E : ServerProxyEvent> register(noinline mapper: FE.(LogicalSide) -> E) {
            onServerRegistered(E::class)
            registerListener(klass) { mapper(LogicalSide.SERVER) }
        }

        inline fun <reified E : ClientProxyEvent> registerClient(noinline mapper: FE.(LogicalSide) -> E) {
            onClientRegistered(E::class)
            registerClientListener(klass) { mapper(LogicalSide.CLIENT) }
        }

        inline fun <reified E> registerBoth(noinline mapper: FE.(LogicalSide) -> E) where E : ServerProxyEvent, E : ClientProxyEvent {
            onServerRegistered(E::class)
            registerListener(klass) { mapper(LogicalSide.SERVER) }
            registerClientListener(klass) { mapper(LogicalSide.CLIENT) }
        }
    }

    protected inline fun <reified FE : PE> on() = ProxyBuilder(FE::class)

    protected abstract fun setupProxyEvents()

    abstract fun setup()
}

fun <T : Any> KClass<*>.allSealedLeaves(): List<KClass<out T>> =
    if (sealedSubclasses.isEmpty()) {
        listOf(this as KClass<out T>)
    } else {
        sealedSubclasses.flatMap { it.allSealedLeaves() }
    }
