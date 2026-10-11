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

enum class RecordType(
    val materialDisplayName: String? = null,
) : StringRepresentable {
    STONE,
    GOLD("Golden"),
    EMERALD,
    DIAMOND,
    NETHERITE,
    BRASS,
    CREATIVE,
    ;

    val id: ResourceLocation = name.lowercase().asResource()
    val propertiesKey: ResourceKey<RecordProperties> = ResourceKey.create(RecordProperties.KEY, id)

    override fun getSerializedName(): String = name.lowercase()

    fun properties(access: RegistryAccess): RecordProperties =
        access.registry(RecordProperties.KEY).map { it.get(propertiesKey) }.orElse(null)
            ?: RecordProperties.FALLBACK

    val uses: Int get() = RecordDurabilityCache[this]

    val isUnbreakable: Boolean get() = uses <= 0

    enum class EffectAttribute(
        val style: Style,
        val qualityIndicator: Boolean = false,
    ) : StringRepresentable {
        BASSY(Style.EMPTY.withColor(TextColor.parseColor("#A53561").orThrow)),
        SHINY(Style.EMPTY.withColor(TextColor.parseColor("#55E1D9").orThrow)),
        RUSTIC(Style.EMPTY.withColor(TextColor.parseColor("#58693C").orThrow)),
        NOISY(Style.EMPTY.withColor(TextColor.parseColor("#17D960").orThrow)),

        CLEAN(Style.EMPTY.withColor(TextColor.parseColor("#F3F3F3").orThrow).withBold(true), true),
        MID(Style.EMPTY.withColor(TextColor.parseColor("#F3F3F3").orThrow).withItalic(true), true),
        MUDDY(Style.EMPTY.withColor(TextColor.parseColor("#F3F3F3").orThrow), true),
        ;

        override fun getSerializedName(): String = name.lowercase()

        fun translatedComponent(): MutableComponent =
            ModLang
                .translate("tooltips.item.ethereal_record.effect_attribute.${name.lowercase()}")
                .component()
                .withStyle(style)

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

    companion object {
        val CODEC: Codec<RecordType> = StringRepresentable.fromEnum(RecordType::values)
    }
}

