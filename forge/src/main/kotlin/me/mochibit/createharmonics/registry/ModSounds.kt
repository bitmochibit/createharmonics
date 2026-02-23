package me.mochibit.createharmonics.registry

import me.mochibit.createharmonics.CreateHarmonicsMod
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object ModSounds : AutoRegistrable {
    private val SOUND_EVENTS: DeferredRegister<SoundEvent> =
        DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, CreateHarmonicsMod.MOD_ID)

    val SLIDING_STONE = registerSoundEvent("sliding_stone")
    val GLITTER = registerSoundEvent("glitter")

    private fun registerSoundEvent(name: String): RegistryObject<SoundEvent> =
        SOUND_EVENTS.register(name) {
            SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(CreateHarmonicsMod.MOD_ID, name),
            )
        }

    override fun register(
        eventBus: IEventBus,
        context: FMLJavaModLoadingContext,
    ) {
        SOUND_EVENTS.register(eventBus)
    }
}
