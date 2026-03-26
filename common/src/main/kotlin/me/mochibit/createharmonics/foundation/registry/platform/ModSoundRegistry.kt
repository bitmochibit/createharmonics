package me.mochibit.createharmonics.foundation.registry.platform

import net.minecraft.sounds.SoundEvent

/**
 * Interface for defining mod sound events that can be used across different platforms.
 */
abstract class ModSoundRegistry<RegistryObjectType> : AbstractCrossPlatformRegistry<RegistryObjectType, SoundEvent>() {
    abstract val slidingStoneSound: SoundEvent
    abstract val glitterSoundEvent: SoundEvent

    companion object {
        lateinit var instance: ModSoundRegistry<*>
            private set
    }

    init {
        instance = this
    }
}
