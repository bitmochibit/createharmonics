package me.mochibit.createharmonics.foundation.eventbus

import kotlin.reflect.KClass

abstract class CommonEventBridge<PE : Any> : PlatformEventBridge<PE>() {
    private val serverTracking: MutableMap<KClass<out ServerProxyEvent>, Boolean> =
        ServerProxyEvent::class
            .allSealedLeaves<ServerProxyEvent>()
            .associateWith { false }
            .toMutableMap()

    override fun onServerRegistered(klass: KClass<out ServerProxyEvent>) {
        check(klass in serverTracking) { "Unknown ServerProxyEvent: ${klass.simpleName}" }
        serverTracking[klass] = true
    }

    override fun setup() {
        setupProxyEvents()
        val missing = serverTracking.filterValues { !it }.keys
        if (missing.isNotEmpty()) {
            error(
                "Unregistered server proxy events!\n" +
                    missing.map { it.qualifiedName } +
                    "\nhttps://github.com/bitmochibit/createharmonics/issues",
            )
        }
    }
}
