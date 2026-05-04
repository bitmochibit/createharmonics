package me.mochibit.createharmonics.foundation.registry.platform

import com.mojang.serialization.Codec
import me.mochibit.createharmonics.foundation.registry.platform.bridge.CommonAbstractRegistry
import net.minecraft.core.component.DataComponentType
import java.util.function.UnaryOperator

object ModDataComponents : CommonAbstractRegistry<DataComponentType<*>>() {
    val recordUrl: DataComponentType<String> by entry("record_url") {
        DataComponentType.builder<String>().persistent(Codec.STRING).build()
    }

    val craftedWith: DataComponentType<String> by entry("crafted_with") {
        DataComponentType.builder<String>().persistent(Codec.STRING).build()
    }
}
