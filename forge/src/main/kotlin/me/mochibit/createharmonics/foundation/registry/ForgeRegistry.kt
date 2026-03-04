package me.mochibit.createharmonics.foundation.registry

import me.mochibit.createharmonics.ModEventBus
import net.minecraftforge.eventbus.api.Event
import net.minecraftforge.eventbus.api.IEventBus

sealed interface ForgeRegistry : Registrable

internal inline fun <reified Marker : RegistrableWithContext<Context>, reified E : Event, Context> eventAutoRegister(
    crossinline contextExtractor: E.() -> Context,
) {
    ModEventBus?.addListener<E> { event ->
        platformEventRegister<Marker, Context> { event.contextExtractor() }
    }
}
