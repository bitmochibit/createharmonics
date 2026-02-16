package me.mochibit.createharmonics.data.lootModifier

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.loot.LootContext
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition
import net.minecraftforge.common.loot.IGlobalLootModifier
import net.minecraftforge.common.loot.LootModifier
import net.minecraftforge.registries.ForgeRegistries

class AddItemModifier(
    conditions: Array<LootItemCondition>,
    val item: Item,
    val chance: Float
) : LootModifier(conditions) {

    override fun doApply(
        generatedLoot: ObjectArrayList<ItemStack>,
        context: LootContext
    ): ObjectArrayList<ItemStack> {

        if (context.random.nextFloat() < chance) {
            generatedLoot.add(ItemStack(item))
        }

        return generatedLoot
    }

    override fun codec(): Codec<out IGlobalLootModifier> = CODEC

    companion object {

        val CODEC: Codec<AddItemModifier> =
            RecordCodecBuilder.create { instance: RecordCodecBuilder.Instance<AddItemModifier> ->

                codecStart(instance).and(
                    instance.group(
                        ForgeRegistries.ITEMS.codec
                            .fieldOf("item")
                            .forGetter { it.item },

                        Codec.FLOAT
                            .fieldOf("chance")
                            .forGetter { it.chance }
                    )
                ).apply(instance) { conditions, item, chance ->
                    AddItemModifier(conditions, item, chance)
                }
            }
    }
}
