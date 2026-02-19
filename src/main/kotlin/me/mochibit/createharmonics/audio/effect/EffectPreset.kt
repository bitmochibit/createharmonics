package me.mochibit.createharmonics.audio.effect

import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld
import me.mochibit.createharmonics.audio.AudioPlayer
import me.mochibit.createharmonics.extension.countLiquidCoveredFaces
import me.mochibit.createharmonics.extension.getManagingShip
import me.mochibit.createharmonics.extension.lerpTo
import me.mochibit.createharmonics.foundation.math.FloatSupplierInterpolated
import net.minecraft.core.Vec3i
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.valkyrienskies.mod.common.getShipManagingPos

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

        val cutoffFrequencyInterpolated = FloatSupplierInterpolated({ targetCutoffFrequency }, 500)
        val resonanceInterpolated = FloatSupplierInterpolated({ targetResonance }, 500)

        private fun applyLowPassFilter(
            audioPlayer: AudioPlayer,
            cutoffFrequency: Float,
            resonance: Float,
        ) {
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

        private fun removeLowPassFilter(audioPlayer: AudioPlayer) {
            targetCutoffFrequency = 20000f
            targetResonance = 0.707f
            val effectChain = audioPlayer.getCurrentEffectChain() ?: return
            val effects = effectChain.getEffects()
            val lowPassIndex = effects.indexOfFirst { it is LowPassFilterEffect }
            if (lowPassIndex >= 0) {
                effectChain.removeEffectAt(lowPassIndex, true)
            }
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

            val managingShip = level.getShipManagingPos(x, y, z)
            val (liquidCoveredFaces, isThick) = level.countLiquidCoveredFaces(x, y, z, managingShip)

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
