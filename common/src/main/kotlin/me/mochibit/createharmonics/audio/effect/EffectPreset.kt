package me.mochibit.createharmonics.audio.effect

import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld
import me.mochibit.createharmonics.audio.player.AudioPlayer
import me.mochibit.createharmonics.foundation.extension.countLiquidCoveredFaces
import me.mochibit.createharmonics.foundation.extension.lerpTo
import me.mochibit.createharmonics.foundation.supplier.values.FloatSupplierInterpolated
import net.minecraft.core.Vec3i
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

sealed interface EffectPreset {
    fun update(
        audioPlayer: AudioPlayer,
        x: Double,
        y: Double,
        z: Double,
        level: Level,
    )

    fun update(
        audioPlayer: AudioPlayer,
        blockPos: Vec3,
        level: Level,
    ) = update(audioPlayer, blockPos.x, blockPos.y, blockPos.z, level)

    fun update(
        audioPlayer: AudioPlayer,
        blockPos: Vec3i,
        level: Level,
    ) = update(audioPlayer, blockPos.x.toDouble(), blockPos.y.toDouble(), blockPos.z.toDouble(), level)

    class UnderwaterFilter : EffectPreset {
        private var targetCutoffFrequency = 20000f
        private var targetResonance = 0.707f

        val cutoffFrequencyInterpolated = FloatSupplierInterpolated({ targetCutoffFrequency }, 900)
        val resonanceInterpolated = FloatSupplierInterpolated({ targetResonance }, 900)

        private fun applyLowPassFilter(
            audioPlayer: AudioPlayer,
            cutoffFrequency: Float,
            resonance: Float,
        ) {
            val effectChain = audioPlayer.effectChain ?: return
            val effects = effectChain.getEffects()
            val existingFilter = effects.firstOrNull { it.scope == AudioEffect.Scope.EXTERNAL_EFFECT } as? LowPassFilterEffect

            if (existingFilter == null) {
                // Force initialization of interpolators at base (no-effect) values before updating the target,
                // so the transition interpolates smoothly from base to the active filter values on add.
                cutoffFrequencyInterpolated.getValue()
                resonanceInterpolated.getValue()
                targetCutoffFrequency = cutoffFrequency
                targetResonance = resonance
                effectChain.addAfterScope(
                    AudioEffect.Scope.MACHINE_CONTROLLED_PITCH,
                    LowPassFilterEffect(
                        cutoffFrequencyInterpolated,
                        resonanceInterpolated,
                        AudioEffect.Scope.EXTERNAL_EFFECT,
                    ),
                )
            } else {
                targetCutoffFrequency = cutoffFrequency
                targetResonance = resonance
            }
        }

        private fun removeLowPassFilter(audioPlayer: AudioPlayer) {
            val effectChain = audioPlayer.effectChain ?: return
            val effects = effectChain.getEffects()
            val lowPassIndex = effects.indexOfFirst { it.scope == AudioEffect.Scope.EXTERNAL_EFFECT }
            if (lowPassIndex < 0) return

            // Ensure interpolators are initialized at the current active values before
            // setting base targets, so the fade-out interpolates smoothly rather than snapping.
            cutoffFrequencyInterpolated.getValue()
            resonanceInterpolated.getValue()
            targetCutoffFrequency = 20000f
            targetResonance = 0.707f
            effectChain.removeEffectAt(lowPassIndex, true)
        }

        override fun update(
            audioPlayer: AudioPlayer,
            x: Double,
            y: Double,
            z: Double,
            level: Level,
        ) {
            if (level is VirtualRenderWorld) return
            if (!level.isClientSide) return

            val (liquidCoveredFaces, isThick) = level.countLiquidCoveredFaces(x, y, z)

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
