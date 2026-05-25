package me.mochibit.createharmonics.foundation.services

import me.mochibit.createharmonics.content.records.AbstractEtherealRecordItemFactory
import net.minecraft.world.level.material.FluidState

interface ContentService {
    // Helpers for platform-specific features
    fun getViscosity(fluidState: FluidState): Int

    val etherealRecordItemFactory: AbstractEtherealRecordItemFactory
}

val contentService: ContentService by lazy {
    loadService<ContentService>()
}
