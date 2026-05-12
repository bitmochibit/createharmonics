package me.mochibit.createharmonics.audio.effect

import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld
import me.mochibit.createharmonics.audio.player.AudioPlayer
import me.mochibit.createharmonics.compat.ModCompats
import me.mochibit.createharmonics.config.ModConfigs
import me.mochibit.createharmonics.foundation.extension.getBlockState
import me.mochibit.createharmonics.foundation.extension.getFluidState
import me.mochibit.createharmonics.foundation.extension.lerpTo
import me.mochibit.createharmonics.foundation.services.contentService
import me.mochibit.createharmonics.foundation.supplier.values.FloatInterpolator
import net.minecraft.core.Direction
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.material.FluidState
import org.joml.Vector3d
import kotlin.time.Duration.Companion.milliseconds

@JvmInline
value class PositionVector(
    val value: Vector3d,
)

@JvmInline
value class CursorVector(
    val value: Vector3d,
)

sealed interface EffectPreset {
    fun update(audioPlayer: AudioPlayer)

    abstract class AbstractEffectPreset : EffectPreset {
        protected val positionVec = PositionVector(Vector3d())

        /**
         * Use this vector for avoiding GC pressure in scans
         */
        protected val cursorVec = CursorVector(Vector3d())

        private var heavyUpdateCooldown = 0
        protected open val heavyUpdateRate = 20

        final override fun update(audioPlayer: AudioPlayer) {
            val ctx = audioPlayer.context ?: return
            val level = ctx.level() ?: return

            ctx.mutatePosition(positionVec.value)

            tick()

            if (--heavyUpdateCooldown <= 0) {
                heavyUpdateCooldown = heavyUpdateRate

                heavyUpdate(
                    audioPlayer,
                    level,
                )
            }
        }

        protected open fun tick() {}

        protected abstract fun heavyUpdate(
            audioPlayer: AudioPlayer,
            level: Level,
        )
    }

    class UnderwaterFilter : AbstractEffectPreset() {
        companion object {
            val effectScope: AudioEffect.Scope =
                AudioEffect.Scope.register(
                    AudioEffect.Scope.DefaultScope(
                        "underwaterFilterScope",
                        100,
                    ),
                )
        }

        val cutoffFrequencyInterpolated = FloatInterpolator(20000f, 900.milliseconds)
        val resonanceInterpolated = FloatInterpolator(0.707f, 900.milliseconds)

        private fun applyLowPassFilter(
            audioPlayer: AudioPlayer,
            cutoffFrequency: Float,
            resonance: Float,
        ) {
            val effectChain = audioPlayer.effectChain
            val effects = effectChain.getEffects()
            val existingFilter = effects.firstOrNull { it.scope == effectScope && it is LowPassFilterEffect }

            if (existingFilter == null) {
                cutoffFrequencyInterpolated.setTarget(cutoffFrequency)
                resonanceInterpolated.setTarget(resonance)
                effectChain.addEffect(
                    LowPassFilterEffect(
                        cutoffFrequencyInterpolated,
                        resonanceInterpolated,
                        effectScope,
                    ),
                )
            } else {
                cutoffFrequencyInterpolated.setTarget(cutoffFrequency)
                resonanceInterpolated.setTarget(resonance)
            }
        }

        private fun removeLowPassFilter(audioPlayer: AudioPlayer) {
            val effectChain = audioPlayer.effectChain
            val effects = effectChain.getEffects()
            val lowPassIndex = effects.indexOfFirst { it.scope == effectScope && it is LowPassFilterEffect }
            if (lowPassIndex < 0) return
            cutoffFrequencyInterpolated.setTarget(20000f)
            resonanceInterpolated.setTarget(0.707f)
            effectChain.removeEffectAt(lowPassIndex, true)
        }

        override fun heavyUpdate(
            audioPlayer: AudioPlayer,
            level: Level,
        ) {
            if (level is VirtualRenderWorld) return
            if (!level.isClientSide) return

            val (liquidCoveredFaces, isThick) = level.countLiquidCoveredFaces(positionVec, cursorVec)

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

        override fun tick() {
            cutoffFrequencyInterpolated.tick()
            resonanceInterpolated.tick()
        }
    }

    /**
     * Applies a [ReverbEffect] whose parameters are modulated by nearby mineral blocks:
     */
    class Reverberator(
        private val scanRadiusProvider: () -> Int = { ModConfigs.client.reverberatorScanRadius.get() },
        private val maxEffectiveBlocks: Int = 8,
    ) : AbstractEffectPreset() {
        var currentlyActive: Boolean = false
            private set

        val roomSizeInterpolated = FloatInterpolator(BASE_ROOM_SIZE, 900.milliseconds)
        val dampingInterpolated = FloatInterpolator(BASE_DAMPING, 900.milliseconds)
        val wetMixInterpolated = FloatInterpolator(BASE_WET_MIX, 900.milliseconds)

        companion object {
            val effectScope: AudioEffect.Scope =
                AudioEffect.Scope.register(
                    AudioEffect.Scope.DefaultScope(
                        "reverberatorScope",
                        130,
                    ),
                )

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
            val existing = effects.firstOrNull { it.scope == effectScope && it is ReverbEffect }

            if (existing == null) {
                currentlyActive = true

                effectChain.addEffect(
                    ReverbEffect(
                        roomSizeSupplier = roomSizeInterpolated,
                        dampingSupplier = dampingInterpolated,
                        wetMixSupplier = wetMixInterpolated,
                        scope = effectScope,
                    ),
                )
            }
        }

        private fun removeReverb(audioPlayer: AudioPlayer) {
            val effectChain = audioPlayer.effectChain
            val effects = effectChain.getEffects()
            val reverbIndex = effects.indexOfFirst { it.scope == effectScope && it is ReverbEffect }
            if (reverbIndex < 0) return

            roomSizeInterpolated.setTarget(BASE_ROOM_SIZE)
            dampingInterpolated.setTarget(BASE_DAMPING)
            wetMixInterpolated.setTarget(BASE_WET_MIX)

            effectChain.removeEffectAt(reverbIndex, true)

            currentlyActive = false
        }

        override fun tick() {
            this.roomSizeInterpolated.tick()
            this.dampingInterpolated.tick()
            this.wetMixInterpolated.tick()
        }

        override fun heavyUpdate(
            audioPlayer: AudioPlayer,
            level: Level,
        ) {
            if (level is VirtualRenderWorld) return
            if (!level.isClientSide) return

            val counts = level.scanReverberatorBlocks(positionVec, scanRadiusProvider(), cursorVec)

            if (counts.total == 0) {
                removeReverb(audioPlayer)
                return
            }

            val cap = maxEffectiveBlocks.toFloat()
            val roomIncreasersT = (counts.roomIncreasers.coerceAtMost(maxEffectiveBlocks)) / cap
            val dampingIncreasersT = (counts.dampingIncreasers.coerceAtMost(maxEffectiveBlocks)) / cap
            val wetIncreasersT = (counts.wetIncreasers.coerceAtMost(maxEffectiveBlocks)) / cap

            roomSizeInterpolated.setTarget(BASE_ROOM_SIZE.lerpTo(MAX_ROOM_SIZE, roomIncreasersT))
            dampingInterpolated.setTarget(BASE_DAMPING.lerpTo(MAX_DAMPING, dampingIncreasersT))
            wetMixInterpolated.setTarget(BASE_WET_MIX.lerpTo(MAX_WET_MIX, wetIncreasersT))

            applyReverb(audioPlayer)
        }
    }
}

private data class BlockCounts(
    val roomIncreasers: Int,
    val dampingIncreasers: Int,
    val wetIncreasers: Int,
) {
    val total: Int get() = roomIncreasers + dampingIncreasers + wetIncreasers
}

private fun Level.scanReverberatorBlocks(
    position: PositionVector,
    scanRadius: Int,
    cursorVector: CursorVector,
): BlockCounts {
    var roomIncreasers = 0
    var dampingIncreasers = 0
    var wetIncreasers = 0

    val inPlotGrid = ModCompats.sableCompat?.isInPlotGrid(this, position.value) == true

    for (dx in -scanRadius..scanRadius) {
        for (dy in -scanRadius..scanRadius) {
            for (dz in -scanRadius..scanRadius) {
                cursorVector.value.set(position.value.x + dx, position.value.y + dy, position.value.z + dz)
                ModCompats.sableCompat?.projectOutOfSubLevel(this, cursorVector.value)

                // In world contribution
                val blockState =
                    this.getBlockState(
                        cursorVector.value.x.toInt(),
                        cursorVector.value.y.toInt(),
                        cursorVector.value.z.toInt(),
                    )

                when (blockState.block) {
                    in EffectPreset.Reverberator.ROOM_INCREASERS_BLOCKS -> roomIncreasers++
                    in EffectPreset.Reverberator.DAMPING_INCREASERS_BLOCKS -> dampingIncreasers++
                    in EffectPreset.Reverberator.WET_INCREASERS_BLOCKS -> wetIncreasers++
                }

                // In physics space contribution

                if (inPlotGrid) {
                    val shipBlockState =
                        this.getBlockState(
                            (position.value.x + dx).toInt(),
                            (position.value.y + dy).toInt(),
                            (position.value.z + dz).toInt(),
                        )
                    when (shipBlockState.block) {
                        Blocks.AMETHYST_BLOCK -> roomIncreasers++
                        Blocks.EMERALD_BLOCK -> dampingIncreasers++
                        Blocks.DIAMOND_BLOCK -> wetIncreasers++
                    }
                }
            }
        }
    }

    return BlockCounts(roomIncreasers, dampingIncreasers, wetIncreasers)
}

private fun Level.countLiquidCoveredFaces(
    position: PositionVector,
    cursorVector: CursorVector,
): Pair<Int, Boolean> {
    var liquidCount = 0
    var viscousCount = 0
    var waterCount = 0

    val inPlot = ModCompats.sableCompat?.isInPlotGrid(this, position.value) == true

    fun accumulateFluid(fluidState: FluidState) {
        liquidCount++
        when {
            contentService.getViscosity(fluidState) > 1000 -> viscousCount++
            fluidState.`is`(FluidTags.WATER) -> waterCount++
        }
    }

    for (direction in Direction.entries) {
        cursorVector.value.set(
            position.value.x + direction.stepX,
            position.value.y + direction.stepY,
            position.value.z + direction.stepZ,
        )
        if (inPlot) {
            ModCompats.sableCompat?.projectOutOfSubLevel(this, cursorVector.value)
        }

        val worldFluid =
            getFluidState(cursorVector.value.x.toInt(), cursorVector.value.y.toInt(), cursorVector.value.z.toInt())
        if (!worldFluid.isEmpty) {
            accumulateFluid(worldFluid)
            continue
        }

        if (inPlot) {
            val shipFluid =
                getFluidState(
                    (position.value.x + direction.stepX).toInt(),
                    (position.value.y + direction.stepY).toInt(),
                    (position.value.z + direction.stepZ).toInt(),
                )
            if (!shipFluid.isEmpty) {
                accumulateFluid(shipFluid)
            }
        }
    }

    val isThick = viscousCount >= waterCount && viscousCount > 0
    return liquidCount to isThick
}
