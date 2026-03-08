package me.mochibit.createharmonics.foundation.extension

import me.mochibit.createharmonics.foundation.services.PlatformService
import me.mochibit.createharmonics.foundation.services.platformService
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block

val tagDomain =
    when (platformService.currentPlatform) {
        // 😭
        PlatformService.Platform.FORGE -> "forge"

        // Thank you 💕
        PlatformService.Platform.NEOFORGE -> "c"
    }

interface Tags<T> {
    val registry: ResourceKey<out Registry<T>>

    object Blocks : Tags<Block> {
        override val registry: ResourceKey<Registry<Block>>
            get() = Registries.BLOCK
    }

    object Items : Tags<Item> {
        override val registry: ResourceKey<Registry<Item>>
            get() = Registries.ITEM
    }
}

infix fun String.butOnForge(forgePath: String): String =
    if (platformService.currentPlatform ==
        PlatformService.Platform.FORGE
    ) {
        forgePath
    } else {
        this
    }

infix fun Tags.Items.withPath(path: String): TagKey<Item> = TagKey.create(registry, tagDomain resPath path)

infix fun Tags.Blocks.withPath(path: String): TagKey<Block> = TagKey.create(registry, tagDomain resPath path)
