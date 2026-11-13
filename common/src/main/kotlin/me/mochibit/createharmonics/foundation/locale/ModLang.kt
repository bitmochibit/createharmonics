package me.mochibit.createharmonics.foundation.locale

import me.mochibit.createharmonics.CreateHarmonicsMod
import net.createmod.catnip.lang.Lang
import net.createmod.catnip.lang.LangBuilder
import net.createmod.catnip.lang.LangNumberFormat
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState

/**
 * Mod lang
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new ModLang
 */
object ModLang : Lang() {
    /**
     * Builder
     *
     * @return the lang builder
     */
    fun builder(): LangBuilder = LangBuilder(CreateHarmonicsMod.MOD_ID)

    /**
     * Translated options
     *
     * @param prefix
     * @param keys
     * @return the mutable list
     */
    fun translatedOptions(
        prefix: String,
        vararg keys: String,
    ): MutableList<Component> {
        val result: MutableList<Component> = ArrayList(keys.size)

        for (key in keys) {
            result.add(translate("$prefix.$key").component())
        }

        return result
    }

    /**
     * Block name
     *
     * @param state
     * @return the lang builder
     */
    fun blockName(state: BlockState): LangBuilder =
        builder().add(
            state
                .block
                .name,
        )

    /**
     * Item name
     *
     * @param stack
     * @return the lang builder
     */
    fun itemName(stack: ItemStack): LangBuilder =
        builder().add(
            stack
                .hoverName
                .copy(),
        )

    /**
     * Number
     *
     * @param d
     * @return the lang builder
     */
    fun number(d: Double): LangBuilder = builder().text(LangNumberFormat.format(d))

    /**
     * Translate
     *
     * @param langKey the lang key
     * @param args
     * @return the lang builder
     */
    fun translate(
        langKey: String,
        vararg args: Any?,
    ): LangBuilder = builder().translate(langKey, *args)

    /**
     * Text
     *
     * @param text
     * @return the lang builder
     */
    fun text(text: String): LangBuilder = builder().text(text)
}

/**
 * Lang provider
 *
 */
interface LangProvider {
    /**
     * Provide lang
     *
     * @param keyValueConsumer the key value consumer
     * @receiver
     */
    fun provideLang(keyValueConsumer: (String, String) -> Unit)

    /**
     * With mod namespace on the receiver [String]
     *
     * @return the string
     */
    fun String.withModNamespace(): String = "${CreateHarmonicsMod.MOD_ID.lowercase()}.$this"
}
