package me.mochibit.createharmonics.foundation.registry

import com.mojang.serialization.Codec
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.ModEventBus
import me.mochibit.createharmonics.data.lootModifier.AddItemModifier
import net.minecraftforge.common.loot.IGlobalLootModifier
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object ModLootModifiers : ForgeRegistry {
    val LOOT_MODIFIERS: DeferredRegister<Codec<out IGlobalLootModifier>> =
        DeferredRegister.create(
            ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,
            CreateHarmonicsMod.MOD_ID,
        )

    val ADD_ITEM: RegistryObject<Codec<AddItemModifier>> =
        LOOT_MODIFIERS.register("add_record_to_end_ship") { AddItemModifier.CODEC }

    override fun register() {
        LOOT_MODIFIERS.register(ModEventBus)
    }
}
