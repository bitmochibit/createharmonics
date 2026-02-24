package me.mochibit.createharmonics.registry

import me.mochibit.createharmonics.ForgeCreateHarmonicsMod
import net.createmod.catnip.lang.Lang
import net.createmod.catnip.lang.LangBuilder
import net.createmod.catnip.lang.LangNumberFormat
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.minecraftforge.fluids.FluidStack

internal object ModLang : Lang() {
    fun builder(): LangBuilder = LangBuilder(ForgeCreateHarmonicsMod.MOD_ID)

    fun blockName(state: BlockState): LangBuilder =
        builder().add(
            state
                .block
                .name,
        )

    fun itemName(stack: ItemStack): LangBuilder =
        builder().add(
            stack
                .getHoverName()
                .copy(),
        )

    fun fluidName(stack: FluidStack): LangBuilder =
        builder().add(
            stack
                .displayName
                .copy(),
        )

    fun number(d: Double): LangBuilder = builder().text(LangNumberFormat.format(d))

    fun translate(
        langKey: String,
        vararg args: Any?,
    ): LangBuilder = builder().translate(langKey, *args)

    fun text(text: String): LangBuilder = builder().text(text)
}
