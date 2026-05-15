package me.mochibit.createharmonics.foundation.registry

import com.simibubi.create.foundation.data.CreateRegistrate
import com.tterrag.registrate.util.entry.RegistryEntry
import me.mochibit.createharmonics.CreateHarmonicsMod.MOD_ID
import me.mochibit.createharmonics.ModRegistrate
import me.mochibit.createharmonics.foundation.info
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent

object ModSounds : CommonRegistry {
    val SLIDING_STONE = ModRegistrate.sound("sliding_stone")
    val GLITTER = ModRegistrate.sound("glitter")

    override fun register() {
        "Registering mod sounds..".info()
    }

    fun CreateRegistrate.sound(
        name: String,
        soundPath: String = name,
    ): RegistryEntry<SoundEvent> =
        this.simple(name, Registries.SOUND_EVENT) {
            SoundEvent.createVariableRangeEvent(ResourceLocation(MOD_ID, soundPath))
        }
}
