package me.mochibit.createharmonics.foundation.shared.forge

import net.minecraft.world.level.material.FluidState

object FluidStateHelperImpl {
    @JvmStatic
    fun getViscosity(fluidState: FluidState): Int = fluidState.fluidType.viscosity
}
