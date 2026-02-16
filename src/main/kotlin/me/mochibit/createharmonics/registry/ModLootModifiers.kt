package me.mochibit.createharmonics.registry

import com.mojang.serialization.Codec
import me.mochibit.createharmonics.data.lootModifier.AddItemModifier
import me.mochibit.createharmonics.CreateHarmonicsMod
import net.minecraftforge.common.loot.IGlobalLootModifier
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject
import java.util.function.Supplier

object ModLootModifiers {
    val LOOT_MODIFIERS: DeferredRegister<Codec<out IGlobalLootModifier?>?> =
        DeferredRegister.create<Codec<out IGlobalLootModifier?>?>(
            ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,
            CreateHarmonicsMod.MOD_ID
        )

    val ADD_ITEM: RegistryObject<Codec<AddItemModifier?>?>? =
        LOOT_MODIFIERS.register<Codec<AddItemModifier?>?>("add_item", Supplier { AddItemModifier.CODEC })
}