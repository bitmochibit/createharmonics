
package me.mochibit.createharmonics.foundation.eventbus

import kotlin.reflect.KClass

abstract class PlatformEventBridge<PlatformEvent : Any> {
    private val registeredServer: MutableMap<KClass<out ServerProxyEvent>, Boolean> =
        ServerProxyEvent::class
            .allSealedLeaves<ServerProxyEvent>()
            .associateWith { false }
            .toMutableMap()

    private val registeredClient: MutableMap<KClass<out ClientProxyEvent>, Boolean> =
        ClientProxyEvent::class
            .allSealedLeaves<ClientProxyEvent>()
            .associateWith { false }
            .toMutableMap()

    fun markServerRegistered(klass: KClass<out ServerProxyEvent>) {
        check(klass in registeredServer) { "Unknown ServerProxyEvent subclass: ${klass.simpleName}" }
        registeredServer[klass] = true
    }

    fun markClientRegistered(klass: KClass<out ClientProxyEvent>) {
        check(klass in registeredClient) { "Unknown ClientProxyEvent subclass: ${klass.simpleName}" }
        registeredClient[klass] = true
    }

    abstract fun <FE : PlatformEvent> registerListener(
        klass: KClass<FE>,
        mapper: FE.() -> ServerProxyEvent,
    )

    abstract fun <FE : PlatformEvent> registerClientListener(
        klass: KClass<FE>,
        mapper: FE.() -> ClientProxyEvent,
    )

    inner class ProxyBuilder<FE : PlatformEvent>(
        val klass: KClass<FE>,
    ) {
        inline fun <reified PE : ServerProxyEvent> register(noinline mapper: FE.(LogicalSide) -> PE) {
            markServerRegistered(PE::class)
            registerListener(klass) { mapper(LogicalSide.SERVER) }
        }

        inline fun <reified PE : ClientProxyEvent> registerClient(noinline mapper: FE.(LogicalSide) -> PE) {
            markClientRegistered(PE::class)
            registerClientListener(klass) { mapper(LogicalSide.CLIENT) }
        }

        inline fun <reified E> registerBoth(
            noinline mapper: FE.(LogicalSide) -> E,
        )
                where E : ServerProxyEvent, E : ClientProxyEvent {
            markServerRegistered(E::class)
            markClientRegistered(E::class)
            registerListener(klass) { mapper(LogicalSide.SERVER) }
            registerClientListener(klass) { mapper(LogicalSide.CLIENT) }
        }
    }

    protected inline fun <reified FE : PlatformEvent> on() = ProxyBuilder(FE::class)

    protected abstract fun setupProxyEvents()

    fun setup() {
        setupProxyEvents()
        val missingServer = registeredServer.filterValues { !it }.keys
        val missingClient = registeredClient.filterValues { !it }.keys
        if (missingServer.isNotEmpty() || missingClient.isNotEmpty()) {
            error(
                "Unregistered proxy events detected! " +
                    "\n\nserver: ${missingServer.map { it.qualifiedName }}" +
                    "\nclient: ${missingClient.map { it.qualifiedName }}" +
                    "\n\nThis is an oversight on the part of Create: Harmonics developers; please open a issue on github to notify them!" +
                    "\nhttps://github.com/bitmochibit/createharmonics/issues",
            )
        }
    }
}

private fun <T : Any> KClass<*>.allSealedLeaves(): List<KClass<out T>> =
    if (sealedSubclasses.isEmpty()) {
        listOf(this as KClass<out T>)
    } else {
        sealedSubclasses.flatMap { it.allSealedLeaves() }
    }
