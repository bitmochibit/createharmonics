package me.mochibit.createharmonics.foundation.eventbus

import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

abstract class ClientEventBridge<PE : Any> : PlatformEventBridge<PE>() {
    private val clientTracking: MutableMap<KClass<out ClientProxyEvent>, Boolean> =
        ClientProxyEvent::class
            .allSealedLeaves<ClientProxyEvent>()
            .filter { !it.isSubclassOf(ServerProxyEvent::class) }
            .associateWith { false }
            .toMutableMap()

    override fun onClientRegistered(klass: KClass<out ClientProxyEvent>) {
        check(klass in clientTracking) { "Unknown client-only ProxyEvent: ${klass.simpleName}" }
        clientTracking[klass] = true
    }

    override fun <FE : PE> registerListener(
        klass: KClass<FE>,
        mapper: FE.() -> ServerProxyEvent,
    ) = error("Use CommonEventBridge for server/both events")

    override fun setup() {
        setupProxyEvents()
        val missing = clientTracking.filterValues { !it }.keys
        if (missing.isNotEmpty()) {
            error(
                "Unregistered client-only proxy events!\n" +
                    missing.map { it.qualifiedName } +
                    "\nhttps://github.com/bitmochibit/createharmonics/issues",
            )
        }
    }
}
