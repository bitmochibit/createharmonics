package me.mochibit.createharmonics.data.lootModifier

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.loot.LootContext
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition
import net.neoforged.neoforge.common.loot.IGlobalLootModifier
import net.neoforged.neoforge.common.loot.LootModifier

class AddItemModifier(
    conditions: Array<LootItemCondition>,
    val item: Item,
    val chance: Float,
) : LootModifier(conditions) {
    override fun doApply(
        generatedLoot: ObjectArrayList<ItemStack>,
        context: LootContext,
    ): ObjectArrayList<ItemStack> {
        if (context.random.nextFloat() < chance) {
            generatedLoot.add(ItemStack(item))
        }
        println("AddItemModifier triggered! Loot size before = ${generatedLoot.size}")

        return generatedLoot
    }

    override fun codec(): MapCodec<out IGlobalLootModifier?> = CODEC

    companion object {
        val CODEC: MapCodec<AddItemModifier> =
            RecordCodecBuilder.mapCodec { instance: RecordCodecBuilder.Instance<AddItemModifier> ->
                codecStart(instance)
                    .and(
                        instance.group(
                            BuiltInRegistries.ITEM
                                .byNameCodec()
                                .fieldOf("item")
                                .forGetter { it.item },
                            Codec.FLOAT
                                .fieldOf("chance")
                                .forGetter { it.chance },
                        ),
                    ).apply(instance) { conditions, item, chance ->
                        AddItemModifier(conditions, item, chance)
                    }
            }
    }
}
