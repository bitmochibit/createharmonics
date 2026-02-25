package me.mochibit.createharmonics.foundation.shared

import dev.architectury.injectables.annotations.ExpectPlatform
import net.minecraft.world.level.material.FluidState

object FluidStateHelper {
    @JvmStatic
    @ExpectPlatform
    fun getViscosity(fluidState: FluidState): Int = throw AssertionError("Platform-specific implementation required")
}
