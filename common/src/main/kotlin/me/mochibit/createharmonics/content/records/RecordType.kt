package me.mochibit.createharmonics.content.records

import com.mojang.serialization.Codec
import me.mochibit.createharmonics.content.records.properties.RecordProperties
import me.mochibit.createharmonics.foundation.extension.asResource
import me.mochibit.createharmonics.foundation.locale.LangProvider
import me.mochibit.createharmonics.foundation.locale.ModLang
import net.minecraft.core.RegistryAccess
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.StringRepresentable
import java.util.Locale.getDefault

/**
 * Record type
 * Enum — a fixed set of named constants.
 *
 * @constructor Creates a new RecordType
 * @property materialDisplayName the material display name
 */
enum class RecordType(
    val materialDisplayName: String? = null,
) : StringRepresentable {
    /**
     * Stone
     *
     * @constructor Creates a new STONE
     */
    STONE,

    /**
     * Gold
     *
     * @constructor Creates a new GOLD
     */
    GOLD("Golden"),

    /**
     * Emerald
     *
     * @constructor Creates a new EMERALD
     */
    EMERALD,

    /**
     * Diamond
     *
     * @constructor Creates a new DIAMOND
     */
    DIAMOND,

    /**
     * Netherite
     *
     * @constructor Creates a new NETHERITE
     */
    NETHERITE,

    /**
     * Brass
     *
     * @constructor Creates a new BRASS
     */
    BRASS,

    /**
     * Creative
     *
     * @constructor Creates a new CREATIVE
     */
    CREATIVE,
    ;

    val id: ResourceLocation = name.lowercase().asResource()
    val propertiesKey: ResourceKey<RecordProperties> = ResourceKey.create(RecordProperties.KEY, id)

    override fun getSerializedName(): String = name.lowercase()

    /**
     * Properties
     *
     * @param access
     * @return the record properties
     */
    fun properties(access: RegistryAccess): RecordProperties =
        access.registry(RecordProperties.KEY).map { it.get(propertiesKey) }.orElse(null)
            ?: RecordProperties.FALLBACK

    val uses: Int get() = RecordDurabilityCache[this]

    val isUnbreakable: Boolean get() = uses <= 0

    /**
     * Effect attribute
     * Enum — a fixed set of named constants.
     *
     * @constructor Creates a new EffectAttribute
     * @property style
     * @property qualityIndicator the quality indicator
     */
    enum class EffectAttribute(
        val style: Style,
        val qualityIndicator: Boolean = false,
    ) : StringRepresentable {
        /**
         * Bassy
         *
         * @constructor Creates a new BASSY
         */
        BASSY(Style.EMPTY.withColor(TextColor.parseColor("#A53561").orThrow)),

        /**
         * Shiny
         *
         * @constructor Creates a new SHINY
         */
        SHINY(Style.EMPTY.withColor(TextColor.parseColor("#55E1D9").orThrow)),

        /**
         * Rustic
         *
         * @constructor Creates a new RUSTIC
         */
        RUSTIC(Style.EMPTY.withColor(TextColor.parseColor("#58693C").orThrow)),

        /**
         * Noisy
         *
         * @constructor Creates a new NOISY
         */
        NOISY(Style.EMPTY.withColor(TextColor.parseColor("#17D960").orThrow)),

        /**
         * Clean
         *
         * @constructor Creates a new CLEAN
         */
        CLEAN(Style.EMPTY.withColor(TextColor.parseColor("#F3F3F3").orThrow).withBold(true), true),

        /**
         * Mid
         *
         * @constructor Creates a new MID
         */
        MID(Style.EMPTY.withColor(TextColor.parseColor("#F3F3F3").orThrow).withItalic(true), true),

        /**
         * Muddy
         *
         * @constructor Creates a new MUDDY
         */
        MUDDY(Style.EMPTY.withColor(TextColor.parseColor("#F3F3F3").orThrow), true),
        ;

        override fun getSerializedName(): String = name.lowercase()

        /**
         * Translated component
         *
         * @return the mutable component
         */
        fun translatedComponent(): MutableComponent =
            ModLang
                .translate("tooltips.item.ethereal_record.effect_attribute.${name.lowercase()}")
                .component()
                .withStyle(style)

        /**
         * Companion
         * Companion object — holds factory methods and constants for the enclosing class.
         *
         * @constructor Creates a new Companion
         */
        companion object : LangProvider {
            val CODEC: Codec<EffectAttribute> = StringRepresentable.fromEnum(EffectAttribute::values)

            override fun provideLang(keyValueConsumer: (String, String) -> Unit) {
                for (attribute in entries) {
                    val attrName = attribute.name.lowercase()
                    keyValueConsumer(
                        "tooltips.item.ethereal_record.effect_attribute.$attrName".withModNamespace(),
                        attrName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() },
                    )
                }
            }
        }
    }

    /**
     * Companion
     * Companion object — holds factory methods and constants for the enclosing class.
     *
     * @constructor Creates a new Companion
     */
    companion object {
        val CODEC: Codec<RecordType> = StringRepresentable.fromEnum(RecordType::values)
    }
}

