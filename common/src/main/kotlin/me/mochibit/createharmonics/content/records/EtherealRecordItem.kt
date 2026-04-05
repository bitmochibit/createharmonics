package me.mochibit.createharmonics.content.records

import me.mochibit.createharmonics.foundation.locale.ModLang
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level

class EtherealRecordItem(
    val recordType: RecordType,
    props: Properties,
) : Item(
        props.apply {
            val maxDamage =
                if (recordType.uses > 0) {
                    recordType.uses + 1
                } else {
                    0
                }
            this.durability(maxDamage)
        },
    ) {
    override fun isDamageable(stack: ItemStack): Boolean = recordType.uses > 0

    override fun getDefaultInstance(): ItemStack {
        val discs =
            BuiltInRegistries.ITEM
                .stream()
                .filter { item -> item.defaultInstance.has(DataComponents.JUKEBOX_PLAYABLE) }
                .toList()

        val default = super.getDefaultInstance()
        RecordCraftingHandler.setCraftedWithDisc(default, ItemStack(discs.random()))
        return default
    }

    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltipComponents: MutableList<Component>,
        isAdvanced: TooltipFlag,
    ) {
        val url = RecordUtilities.getAudioUrl(stack)
        if (!url.isNullOrBlank()) {
            tooltipComponents.add(
                ModLang.translate("tooltips.item.ethereal_record.url_bound").component().withStyle(ChatFormatting.GRAY),
            )
        }

        val effectAttributes = this.recordType.properties.effectAttributes
        if (effectAttributes.isNotEmpty()) {
            tooltipComponents.add(Component.empty())
            tooltipComponents.add(ModLang.translate("tooltips.item.ethereal_record.qualities").component().withStyle(ChatFormatting.GRAY))
            val attributeComponent =
                effectAttributes
                    .sortedByDescending { it.qualityIndicator }
                    .map {
                        Component
                            .empty()
                            .withStyle(Style.EMPTY)
                            .append(it.translatedComponent())
                    }.reduceOrNull { acc, c ->
                        acc
                            .append(
                                Component.literal(", ").setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)),
                            ).append(c)
                    }
                    ?: Component.empty()
            tooltipComponents.add(CommonComponents.space().plainCopy().append(attributeComponent))
        }
        super.appendHoverText(stack, context, tooltipComponents, isAdvanced)
    }
}
