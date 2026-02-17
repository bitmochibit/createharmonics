package me.mochibit.createharmonics.audio.effect

import me.mochibit.createharmonics.audio.AudioPlayer
import me.mochibit.createharmonics.extension.countLiquidCoveredFaces
import me.mochibit.createharmonics.extension.getManagingShip
import me.mochibit.createharmonics.extension.lerpTo
import me.mochibit.createharmonics.foundation.math.FloatSupplierInterpolated
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import kotlin.compareTo

sealed interface EffectPreset {
    fun update(
        audioPlayer: AudioPlayer,
        blockPos: BlockPos,
        level: Level,
    )

    class UnderwaterFilter : EffectPreset {
        private var targetCutoffFrequency = 20000f
        private var targetResonance = 0.707f

        val cutoffFrequencyInterpolated = FloatSupplierInterpolated({ targetCutoffFrequency }, 500)
        val resonanceInterpolated = FloatSupplierInterpolated({ targetResonance }, 500)

        /**
         * Updates or adds a low-pass filter with the specified parameters.
         * If the filter exists, updates its parameters. Otherwise, adds a new filter.
         */
        private fun applyLowPassFilter(
            audioPlayer: AudioPlayer,
            cutoffFrequency: Float,
            resonance: Float,
        ) {
            // Update target values for interpolation
            targetCutoffFrequency = cutoffFrequency
            targetResonance = resonance

            val effectChain = audioPlayer.getCurrentEffectChain() ?: return
            val effects = effectChain.getEffects()
            val existingFilter = effects.firstOrNull { it is LowPassFilterEffect } as? LowPassFilterEffect

            if (existingFilter == null) {
                effectChain.addEffect(
                    LowPassFilterEffect(
                        cutoffFrequencyInterpolated,
                        resonanceInterpolated,
                    ),
                )
            }
        }

        /**
         * Removes the low-pass filter from the effect chain if it exists.
         * Smoothly transitions back to default values before removing.
         */
        private fun removeLowPassFilter(audioPlayer: AudioPlayer) {
            targetCutoffFrequency = 20000f // Very high cutoff = no filtering
            targetResonance = 0.707f // Flat response
            val effectChain = audioPlayer.getCurrentEffectChain() ?: return
            val effects = effectChain.getEffects()
            val lowPassIndex = effects.indexOfFirst { it is LowPassFilterEffect }
            if (lowPassIndex >= 0) {
                effectChain.removeEffectAt(lowPassIndex, true)
            }
        }

        override fun update(
            audioPlayer: AudioPlayer,
            blockPos: BlockPos,
            level: Level,
        ) {
            if (!level.isClientSide) return

            val managingShip = blockPos.getManagingShip(level)
            val (liquidCoveredFaces, isThick) = blockPos.countLiquidCoveredFaces(level, managingShip)

            if (liquidCoveredFaces > 0) {
                val maxEffectiveFaces = 4f
                val minimumCutoff = if (isThick) 200f else 300f
                val maximumResonance = if (isThick) 2.5f else 2f

                val faceCount = liquidCoveredFaces.coerceAtMost(maxEffectiveFaces.toInt())
                val cutoffFrequency = 1800f.lerpTo(minimumCutoff, 1 / maxEffectiveFaces * faceCount)
                val resonance = 1f.lerpTo(maximumResonance, 1 / maxEffectiveFaces * faceCount)

                applyLowPassFilter(audioPlayer, cutoffFrequency, resonance)
            } else {
                removeLowPassFilter(audioPlayer)
            }
        }
    }
}
