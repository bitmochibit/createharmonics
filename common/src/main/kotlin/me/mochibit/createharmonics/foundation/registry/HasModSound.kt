package me.mochibit.createharmonics.foundation.registry

import net.minecraft.sounds.SoundEvent

interface HasModSound<RegistryObjectType> : CrossPlatformRegistry<RegistryObjectType, SoundEvent> {
    val slidingStoneSound: CrossPlatformRegistry.ConvertibleEntry<RegistryObjectType, SoundEvent>
    val glitterSoundEvent: CrossPlatformRegistry.ConvertibleEntry<RegistryObjectType, SoundEvent>
}
