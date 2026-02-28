package me.mochibit.createharmonics.foundation.locale

import me.mochibit.createharmonics.CreateHarmonicsMod
import net.createmod.catnip.lang.Lang
import net.createmod.catnip.lang.LangBuilder
import net.createmod.catnip.lang.LangNumberFormat
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState

object ModLang : Lang() {
    fun builder(): LangBuilder = LangBuilder(CreateHarmonicsMod.MOD_ID)

    fun blockName(state: BlockState): LangBuilder =
        builder().add(
            state
                .block
                .name,
        )

    fun itemName(stack: ItemStack): LangBuilder =
        builder().add(
            stack
                .hoverName
                .copy(),
        )

    fun number(d: Double): LangBuilder = builder().text(LangNumberFormat.format(d))

    fun translate(
        langKey: String,
        vararg args: Any?,
    ): LangBuilder = builder().translate(langKey, *args)

    fun text(text: String): LangBuilder = builder().text(text)
}
