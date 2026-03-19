package me.mochibit.createharmonics.foundation.registry.platform

import net.minecraft.sounds.SoundEvent

/**
 * Interface for defining mod sound events that can be used across different platforms.
 */
abstract class ModSoundRegistry<RegistryObjectType> : CrossPlatformRegistry<RegistryObjectType, SoundEvent> {
    override val referenceMap: MutableMap<SoundEvent, RegistryObjectType> = mutableMapOf()
    abstract val slidingStoneSound: SoundEvent
    abstract val glitterSoundEvent: SoundEvent
}
