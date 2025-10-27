package me.mochibit.createharmonics.event.recordPlayer

import me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerBlockEntity
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.eventbus.api.Event

class RecordPlayerPlayEvent(
    val playerBe: RecordPlayerBlockEntity,
    val audioResourceLocation: ResourceLocation
): Event() {
}