package me.mochibit.createharmonics.audio.effect

import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld
import me.mochibit.createharmonics.audio.player.AudioPlayer
import me.mochibit.createharmonics.config.ClientConfig
import me.mochibit.createharmonics.config.ModConfigs
import me.mochibit.createharmonics.foundation.extension.countLiquidCoveredFaces
import me.mochibit.createharmonics.foundation.extension.lerpTo
import me.mochibit.createharmonics.foundation.extension.scanReverberatorBlocks
import me.mochibit.createharmonics.foundation.supplier.values.FloatSupplierInterpolated
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.Tags

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
            val effectChain = audioPlayer.effectChain
            val effects = effectChain.getEffects()
            val existingFilter = effects.firstOrNull { it.scope == AudioEffect.Scope.EXTERNAL_EFFECT && it is LowPassFilterEffect }

            if (existingFilter == null) {
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
            val effectChain = audioPlayer.effectChain
            val effects = effectChain.getEffects()
            val lowPassIndex = effects.indexOfFirst { it.scope == AudioEffect.Scope.EXTERNAL_EFFECT && it is LowPassFilterEffect }
            if (lowPassIndex < 0) return

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

    /**
     * Applies a [ReverbEffect] whose parameters are modulated by nearby mineral blocks:
     */
    class Reverberator(
        private val scanRadius: Int = ModConfigs.client.reverberatorScanRadius.get(),
        private val maxEffectiveBlocks: Int = 8,
    ) : EffectPreset {
        // Base values when no blocks are present (near-dry, subtle room)
        private var targetRoomSize = BASE_ROOM_SIZE
        private var targetDamping = BASE_DAMPING
        private var targetWetMix = BASE_WET_MIX

        val roomSizeInterpolated = FloatSupplierInterpolated({ targetRoomSize }, INTERPOLATION_STEPS)
        val dampingInterpolated = FloatSupplierInterpolated({ targetDamping }, INTERPOLATION_STEPS)
        val wetMixInterpolated = FloatSupplierInterpolated({ targetWetMix }, INTERPOLATION_STEPS)

        companion object {
            private const val INTERPOLATION_STEPS = 900L

            private const val BASE_ROOM_SIZE = 0.25f
            private const val BASE_DAMPING = 0.6f
            private const val BASE_WET_MIX = 0.0f // fully dry → effect fades out naturally

            private const val MAX_ROOM_SIZE = 0.95f
            private const val MIN_DAMPING = 0.05f
            private const val MAX_DAMPING = 1.0f
            private const val MAX_WET_MIX = 0.75f

            val ROOM_INCREASERS_BLOCKS: List<Block> =
                listOf(
                    Blocks.AMETHYST_BLOCK,
                )

            val DAMPING_INCREASERS_BLOCKS: List<Block> =
                listOf(
                    Blocks.SPONGE,
                    Blocks.WET_SPONGE,
                )

            val WET_INCREASERS_BLOCKS: List<Block> =
                listOf(
                    Blocks.PRISMARINE,
                    Blocks.PRISMARINE_BRICKS,
                    Blocks.DARK_PRISMARINE,
                )
        }

        private fun applyReverb(audioPlayer: AudioPlayer) {
            val effectChain = audioPlayer.effectChain
            val effects = effectChain.getEffects()
            val existing = effects.firstOrNull { it.scope == AudioEffect.Scope.EXTERNAL_EFFECT && it is ReverbEffect }

            if (existing == null) {
                roomSizeInterpolated.getValue()
                dampingInterpolated.getValue()
                wetMixInterpolated.getValue()

                effectChain.addAfterScope(
                    AudioEffect.Scope.EXTERNAL_EFFECT,
                    ReverbEffect(
                        roomSizeSupplier = roomSizeInterpolated,
                        dampingSupplier = dampingInterpolated,
                        wetMixSupplier = wetMixInterpolated,
                        scope = AudioEffect.Scope.EXTERNAL_EFFECT,
                    ),
                )
            }
        }

        private fun removeReverb(audioPlayer: AudioPlayer) {
            val effectChain = audioPlayer.effectChain
            val effects = effectChain.getEffects()
            val reverbIndex = effects.indexOfFirst { it.scope == AudioEffect.Scope.EXTERNAL_EFFECT && it is ReverbEffect }
            if (reverbIndex < 0) return

            roomSizeInterpolated.getValue()
            dampingInterpolated.getValue()
            wetMixInterpolated.getValue()

            targetRoomSize = BASE_ROOM_SIZE
            targetDamping = BASE_DAMPING
            targetWetMix = BASE_WET_MIX

            effectChain.removeEffectAt(reverbIndex, true)
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

            val counts = level.scanReverberatorBlocks(x, y, z, scanRadius)

            if (counts.total == 0) {
                removeReverb(audioPlayer)
                return
            }

            val cap = maxEffectiveBlocks.toFloat()
            val roomIncreasersT = (counts.roomIncreasers.coerceAtMost(maxEffectiveBlocks)) / cap
            val dampingIncreasersT = (counts.dampingIncreasers.coerceAtMost(maxEffectiveBlocks)) / cap
            val wetIncreasersT = (counts.wetIncreasers.coerceAtMost(maxEffectiveBlocks)) / cap

            targetRoomSize = BASE_ROOM_SIZE.lerpTo(MAX_ROOM_SIZE, roomIncreasersT)
            targetDamping = BASE_DAMPING.lerpTo(MAX_DAMPING, dampingIncreasersT)
            targetWetMix = BASE_WET_MIX.lerpTo(MAX_WET_MIX, wetIncreasersT)

            applyReverb(audioPlayer)
        }
    }
}
