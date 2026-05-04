package me.mochibit.createharmonics.foundation.registry.platform

import me.mochibit.createharmonics.CreateHarmonicsMod.MOD_ID
import me.mochibit.createharmonics.foundation.registry.platform.bridge.CommonAbstractRegistry
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent

/**
 * Interface for defining mod sound events that can be used across different platforms.
 */
object ModSoundRegistry : CommonAbstractRegistry<SoundEvent>() {
    val slidingStone by entry("sliding_stone") {
        SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "sliding_stone"))
    }
    val glitter by entry("glitter") {
        SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "glitter"))
    }
}
