package me.mochibit.createharmonics.content.records

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.simibubi.create.AllItems
import me.mochibit.createharmonics.foundation.registry.ModRecipeTypes
import net.minecraft.core.HolderLookup
import net.minecraft.core.RegistryAccess
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.util.StringRepresentable
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeInput
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.Level
import kotlin.math.ceil
import kotlin.math.ln

enum class RepairKind(private val serial: String, val priority: Int) : StringRepresentable {
    GLUE("glue", 200),
    FULL("full", 150),
    PARTIAL("partial", 120),
    ;

    override fun getSerializedName() = serial

    companion object {
        val CODEC: Codec<RepairKind> = StringRepresentable.fromEnum(RepairKind::values)
    }
}

class RecordRepairRecipe(
    val recordTypes: Set<RecordType>,
    val material: Ingredient,
    val kind: RepairKind,
    val fraction: Float,
) : Recipe<RecipeInput> {

    fun matches(record: ItemStack, held: ItemStack): Boolean {
        val item = record.item as? EtherealRecordItem ?: return false
        val type = item.recordType
        if (type !in recordTypes || type.isUnbreakable) return false
        if (!item.isRecordBroken() && record.damageValue <= 0) return false
        if (!material.test(held)) return false
        return kind != RepairKind.GLUE || held.damageValue < held.maxDamage
    }

    fun repair(record: ItemStack): ItemStack {
        val item = record.item as? EtherealRecordItem ?: return ItemStack.EMPTY
        val max = record.maxDamage
        val broken = item.isRecordBroken()

        val result = if (broken) RecordUtilities.fromBrokenRecordStack(record) else record.copy()
        result.damageValue = when (kind) {
            RepairKind.GLUE, RepairKind.FULL -> 0
            RepairKind.PARTIAL -> {
                val amount = (fraction * max).toInt().coerceAtLeast(1)
                if (broken) (max - amount - 1).coerceAtLeast(0)
                else (record.damageValue - amount).coerceAtLeast(0)
            }
        }
        return result
    }

    override fun matches(input: RecipeInput, level: Level) =
        input.size() >= 2 && matches(input.getItem(0), input.getItem(1))

    override fun assemble(input: RecipeInput, registries: HolderLookup.Provider): ItemStack = ItemStack.EMPTY
    override fun canCraftInDimensions(width: Int, height: Int) = false
    override fun getResultItem(registries: HolderLookup.Provider): ItemStack = ItemStack.EMPTY
    override fun isSpecial() = true
    override fun getSerializer(): RecipeSerializer<*> = ModRecipeTypes.RECORD_REPAIR_SERIALIZER.get()
    override fun getType(): RecipeType<*> = ModRecipeTypes.RECORD_REPAIR.get()

    object Serializer : RecipeSerializer<RecordRepairRecipe> {
        override fun codec() = CODEC
        override fun streamCodec() = STREAM_CODEC
    }

    companion object {
        val CODEC: MapCodec<RecordRepairRecipe> = RecordCodecBuilder.mapCodec { i ->
            i.group(
                RecordType.CODEC.listOf().xmap({ it.toSet() }, { it.toList() })
                    .optionalFieldOf("record_types", RecordType.entries.toSet()).forGetter { it.recordTypes },
                Ingredient.CODEC_NONEMPTY.fieldOf("material").forGetter { it.material },
                RepairKind.CODEC.fieldOf("mode").forGetter { it.kind },
                Codec.floatRange(0.0001f, 1f).optionalFieldOf("fraction", 1f).forGetter { it.fraction },
            ).apply(i, ::RecordRepairRecipe)
        }


        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, RecordRepairRecipe> =
            ByteBufCodecs.fromCodecWithRegistries(CODEC.codec())

        fun calculateGlueRepairCost(maxDamage: Int, currentDamage: Int): Int {
            if (maxDamage <= 0) return 0
            val glueMaxDamage = ItemStack(AllItems.SUPER_GLUE.get()).maxDamage
            val repairRatio = currentDamage.toDouble() / maxDamage.toDouble()
            val logScale = ln(1.0 + maxDamage.toDouble() / glueMaxDamage.toDouble()) / ln(2.0)
            return ceil(logScale * repairRatio * 4.4).toInt().coerceIn(1, glueMaxDamage)
        }
    }
}