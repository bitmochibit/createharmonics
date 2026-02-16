package me.mochibit.createharmonics.registry

import com.mojang.serialization.Codec
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.data.lootModifier.AddItemModifier
import net.minecraftforge.common.loot.IGlobalLootModifier
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object ModLootModifiers: AutoRegistrable {

    val LOOT_MODIFIERS: DeferredRegister<Codec<out IGlobalLootModifier>> =
        DeferredRegister.create(
            ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,
            CreateHarmonicsMod.MOD_ID
        )

    val ADD_ITEM: RegistryObject<Codec<AddItemModifier>> =
        LOOT_MODIFIERS.register("add_record_to_end_ship") { AddItemModifier.CODEC }
    

    override fun register(
        eventBus: IEventBus,
        context: FMLJavaModLoadingContext
    ) {
        LOOT_MODIFIERS.register(eventBus)
        println("Registering loot modifiers!")

    }
}