package me.mochibit.createharmonics.foundation.services

import me.mochibit.createharmonics.audio.instance.SimpleStreamSoundInstance
import me.mochibit.createharmonics.audio.instance.StreamingSoundInstance
import me.mochibit.createharmonics.foundation.supplier.values.FloatSupplier
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.material.FluidState
import java.io.InputStream

class ForgeContentService : ContentService {
    override fun getViscosity(fluidState: FluidState): Int = fluidState.fluidType.viscosity
}
