package me.mochibit.createharmonics.audio.player

import me.mochibit.createharmonics.audio.effect.EffectPreset
import me.mochibit.createharmonics.foundation.supplier.values.FloatInterpolator
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import kotlin.time.Duration.Companion.seconds


class SpatialAudioController {
    val masterVolume = FloatInterpolator(1f, 4.0.seconds)
    val masterPitch = FloatInterpolator(1f, 4.0.seconds)
    val masterRadius = FloatInterpolator(1f, 4.0.seconds)

    val underwaterFilter = EffectPreset.UnderwaterFilter()
    val reverberator = EffectPreset.Reverberator()

    private val effectPresets: List<EffectPreset.AbstractEffectPreset> =
        listOf(underwaterFilter, reverberator)

    fun tick(player: AudioPlayer) {
        val ctx = player.spatialContext ?: return

        masterPitch.setTarget(ctx.targetPitch())
        masterVolume.setTarget(ctx.targetVolume())
        masterRadius.setTarget(ctx.targetRadius())

        masterPitch.tick()
        masterVolume.tick()
        masterRadius.tick()

        for (preset in effectPresets) {
            preset.update(player)
        }
    }

    fun notifyBlockUpdate(player: AudioPlayer, level: Level, pos: BlockPos) {
        for (preset in effectPresets) {
            val radius = preset.externalUpdateRadius
            if (radius <= 0f) continue

            val distSq = preset.currentPosition.distanceSquared(
                pos.x + 0.5, pos.y + 0.5, pos.z + 0.5,
            )
            if (distSq <= radius * radius) {
                preset.onExternalUpdate(player, level)
            }
        }
    }
}
