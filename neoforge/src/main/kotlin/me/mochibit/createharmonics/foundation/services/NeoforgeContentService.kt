package me.mochibit.createharmonics.foundation.services

import net.minecraft.world.level.material.FluidState

class NeoforgeContentService : ContentService {
    override fun getViscosity(fluidState: FluidState): Int = fluidState.fluidType.viscosity
}

