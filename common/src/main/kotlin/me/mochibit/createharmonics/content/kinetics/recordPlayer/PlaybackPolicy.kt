package me.mochibit.createharmonics.content.kinetics.recordPlayer

data class PlaybackInputs(
    val hasDisc: Boolean, val isSpinning: Boolean, val pauseMode: Boolean, val redstonePower: Int,
    val state: PlaybackState, val modeChanged: Boolean,
    val endedNaturally: Boolean, val speedInterrupted: Boolean, val looping: Boolean,
)

data class PlaybackDecision(
    val state: PlaybackState, val startsNewPlay: Boolean = false,
    val endedNaturally: Boolean, val speedInterrupted: Boolean, val looping: Boolean,
)

object PlaybackPolicy {
    fun decide(i: PlaybackInputs): PlaybackDecision {
        val keep = PlaybackDecision(i.state, false, i.endedNaturally, i.speedInterrupted, i.looping)
        return when {
            !i.hasDisc -> keep.copy(state = PlaybackState.STOPPED, endedNaturally = false)
            !i.isSpinning ->
                if (i.state == PlaybackState.PLAYING) keep.copy(state = PlaybackState.PAUSED, speedInterrupted = true) else keep
            i.pauseMode -> pauseMode(i, keep)
            else -> playMode(i, keep)
        }
    }

    private fun playMode(i: PlaybackInputs, keep: PlaybackDecision): PlaybackDecision {
        if (i.redstonePower == RecordPlayerSettings.MAX_POWER) {
            val k = keep.copy(looping = true, endedNaturally = false)
            return if (i.state != PlaybackState.PLAYING)
                k.copy(state = PlaybackState.PLAYING, startsNewPlay = i.state == PlaybackState.STOPPED)
            else k
        }
        val k = keep.copy(looping = false)
        return when (i.state) {
            PlaybackState.STOPPED ->
                if (!i.endedNaturally) k.copy(state = PlaybackState.PLAYING, startsNewPlay = true) else k
            PlaybackState.PAUSED -> when {
                i.modeChanged -> k.copy(state = PlaybackState.PLAYING)
                i.speedInterrupted -> k.copy(state = PlaybackState.PLAYING, speedInterrupted = false)
                else -> k
            }
            PlaybackState.PLAYING -> k
        }
    }

    private fun pauseMode(i: PlaybackInputs, keep: PlaybackDecision): PlaybackDecision =
        if (i.redstonePower > 0) {
            val k = keep.copy(looping = true, endedNaturally = false)
            if (i.state == PlaybackState.PLAYING) k
            else k.copy(state = PlaybackState.PLAYING, startsNewPlay = i.state == PlaybackState.STOPPED)
        } else {
            val k = keep.copy(looping = false)
            if (i.state == PlaybackState.PLAYING) k.copy(state = PlaybackState.PAUSED, speedInterrupted = false) else k
        }
}