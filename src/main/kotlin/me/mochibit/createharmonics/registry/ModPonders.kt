package me.mochibit.createharmonics.registry

import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags
import com.tterrag.registrate.util.entry.ItemProviderEntry
import com.tterrag.registrate.util.entry.RegistryEntry
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.foundation.ponder.PonderScenes
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext

object ModPonders : AutoRegistrable {
    override val registrationOrder = 5

    fun addTags(rawHelper: PonderTagRegistrationHelper<ResourceLocation>) {
        val helper = rawHelper.withKeyFunction(RegistryEntry<*>::getId)

        helper
            .addToTag(
                AllCreatePonderTags.ARM_TARGETS,
                AllCreatePonderTags.DISPLAY_SOURCES,
                AllCreatePonderTags.KINETIC_APPLIANCES,
            ).add(ModBlocks.ANDESITE_JUKEBOX)

        helper
            .addToTag(AllCreatePonderTags.KINETIC_APPLIANCES)
            .add(ModBlocks.RECORD_PRESS_BASE)
    }

    fun addScenes(rawHelper: PonderSceneRegistrationHelper<ResourceLocation>) {
        val helper: PonderSceneRegistrationHelper<ItemProviderEntry<*>?> =
            rawHelper.withKeyFunction(RegistryEntry<*>::getId)

        helper.addStoryBoard(
            ModBlocks.ANDESITE_JUKEBOX,
            "andesite_jukebox",
            PonderScenes::andesiteJukebox,
            AllCreatePonderTags.KINETIC_APPLIANCES,
        )

        helper.addStoryBoard(
            ModBlocks.RECORD_PRESS_BASE,
            "record_press_base",
            PonderScenes::recordPressBase,
            AllCreatePonderTags.KINETIC_APPLIANCES,
        )
    }

    override fun register(
        eventBus: IEventBus,
        context: FMLJavaModLoadingContext,
    ) {
        Logger.info("Loading ponders")
    }
}
