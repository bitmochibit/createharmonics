package me.mochibit.createharmonics.registry

import net.minecraftforge.eventbus.api.IEventBus

interface AbstractModRegistry {
    fun register(eventBus: IEventBus)
}
