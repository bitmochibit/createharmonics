package me.mochibit.createharmonics.foundation.registry

import net.minecraft.sounds.SoundEvent

/**
 * Interface for defining mod sound events that can be used across different platforms.
 */
interface ModSoundRegistry<RegistryObjectType> : CrossPlatformRegistry<RegistryObjectType, SoundEvent> {
    val slidingStoneSound: SoundEvent
    val glitterSoundEvent: SoundEvent
}
