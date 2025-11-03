package me.mochibit.createharmonics.audio.instance

import net.minecraft.client.resources.sounds.Sound
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.SoundManager
import net.minecraft.client.sounds.WeighedSoundEvents
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource

class MovingSoundInstance(
    private val resourceLocation: ResourceLocation,
    private val position: BlockPos,
): SoundInstance {
    override fun getLocation(): ResourceLocation {
        return resourceLocation
    }

    override fun resolve(pManager: SoundManager): WeighedSoundEvents? {
        TODO("Not yet implemented")
    }

    override fun getSound(): Sound {
        TODO("Not yet implemented")
    }

    override fun getSource(): SoundSource {
        TODO("Not yet implemented")
    }

    override fun isLooping(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isRelative(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getDelay(): Int {
        TODO("Not yet implemented")
    }

    override fun getVolume(): Float {
        TODO("Not yet implemented")
    }

    override fun getPitch(): Float {
        TODO("Not yet implemented")
    }

    override fun getX(): Double {
        TODO("Not yet implemented")
    }

    override fun getY(): Double {
        TODO("Not yet implemented")
    }

    override fun getZ(): Double {
        TODO("Not yet implemented")
    }

    override fun getAttenuation(): SoundInstance.Attenuation {
        TODO("Not yet implemented")
    }

}