package me.mochibit.createharmonics.foundation.registry

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.ModEventBus
import me.mochibit.createharmonics.data.lootModifier.AddItemModifier
import net.neoforged.neoforge.common.loot.IGlobalLootModifier
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NeoForgeRegistries
import java.util.function.Supplier

object ModLootModifiers : NeoforgeRegistry {
    val LOOT_MODIFIERS: DeferredRegister<MapCodec<out IGlobalLootModifier>> =
        DeferredRegister.create(
            NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,
            CreateHarmonicsMod.MOD_ID,
        )

    val ADD_ITEM: DeferredHolder<MapCodec<out IGlobalLootModifier>, MapCodec<out IGlobalLootModifier>> =
        LOOT_MODIFIERS.register(
            "add_record_to_end_ship",
            Supplier<MapCodec<out IGlobalLootModifier>> {
                AddItemModifier.CODEC.fieldOf("modifier")
            },
        )

    override fun register() {
        LOOT_MODIFIERS.register(ModEventBus)
    }
}
