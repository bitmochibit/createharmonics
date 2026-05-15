package me.mochibit.createharmonics.foundation.services

import net.minecraft.world.level.material.FluidState

interface ContentService {
    // Helpers for platform-specific features
    fun getViscosity(fluidState: FluidState): Int
}

val contentService: ContentService by lazy {
    loadService<ContentService>()
}
