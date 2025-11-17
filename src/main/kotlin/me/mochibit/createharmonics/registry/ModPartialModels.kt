package me.mochibit.createharmonics.registry

import dev.engine_room.flywheel.lib.model.baked.PartialModel
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext

object ModPartialModels : AbstractModRegistry {

    val ETHEREAL_RECORD: PartialModel = block("ethereal_record_visual")

    private fun block(path: String): PartialModel {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateHarmonicsMod.MOD_ID, "block/" + path))
    }

    private fun entity(path: String): PartialModel {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateHarmonicsMod.MOD_ID, "entity/" + path))
    }

    override fun register(eventBus: IEventBus, context: FMLJavaModLoadingContext) {
        Logger.info("Registering partial models")
    }
}
