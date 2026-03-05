package me.mochibit.createharmonics.foundation.registry

import me.mochibit.createharmonics.ModEventBus
import me.mochibit.createharmonics.foundation.eventbus.EventBus
import me.mochibit.createharmonics.foundation.eventbus.ModEvent
import net.minecraftforge.eventbus.api.Event
import net.minecraftforge.eventbus.api.IEventBus

sealed interface ForgeRegistry : Registrable

internal inline fun <reified ForgeEvent : Event> registerEventProxy(crossinline eventSupplier: ForgeEvent.() -> ModEvent) {
    val bus = ModEventBus ?: throw IllegalStateException("ModEventBus is not initialized")
    bus.addListener<ForgeEvent> { event ->
        EventBus.post(event.eventSupplier())
    }
}
