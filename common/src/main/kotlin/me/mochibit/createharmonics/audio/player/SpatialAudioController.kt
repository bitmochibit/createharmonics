package me.mochibit.createharmonics.audio.player

import me.mochibit.createharmonics.audio.effect.EffectPreset
import me.mochibit.createharmonics.foundation.supplier.values.FloatInterpolator
import kotlin.time.Duration.Companion.seconds

class SpatialAudioController {
    val masterVolume = FloatInterpolator(1f, 4.0.seconds)
    val masterPitch = FloatInterpolator(1f, 4.0.seconds)
    val masterRadius = FloatInterpolator(1f, 4.0.seconds)

    val underwaterFilter = EffectPreset.UnderwaterFilter()
    val reverberator = EffectPreset.Reverberator()

    fun tick(player: AudioPlayer) {
        val ctx = player.context ?: return

        masterPitch.setTarget(ctx.targetPitch())
        masterVolume.setTarget(ctx.targetVolume())
        masterRadius.setTarget(ctx.targetRadius())

        masterPitch.tick()
        masterVolume.tick()
        masterRadius.tick()

        underwaterFilter.update(player)
        reverberator.update(player)
    }
}

