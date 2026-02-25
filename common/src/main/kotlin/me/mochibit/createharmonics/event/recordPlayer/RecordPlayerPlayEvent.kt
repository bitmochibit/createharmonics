package me.mochibit.createharmonics.event.recordPlayer

import dev.architectury.event.EventFactory
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlockEntity
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.eventbus.api.Event

object RecordPlayerPlayEvent {
    @JvmField
    val EVENT: EventFactory<Listener> = EventFactory.createLoop()

    fun interface Listener {
        fun onPlay(
            playerBe: RecordPlayerBlockEntity,
            audioResourceLocation: ResourceLocation,
        )
    }
}
