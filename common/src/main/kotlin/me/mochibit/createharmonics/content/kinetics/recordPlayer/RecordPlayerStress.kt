package me.mochibit.createharmonics.content.kinetics.recordPlayer

object RecordPlayerStress {
    fun impact(base: Float, state: PlaybackState, speedInterrupted: Boolean, staticPitch: Boolean, rpm: Float) = when {
        state == PlaybackState.STOPPED -> 0f
        state == PlaybackState.PAUSED && !speedInterrupted -> base / 4f
        staticPitch && rpm > 0f && rpm <= PitchCurve.UNITY_RPM -> base * (PitchCurve.UNITY_RPM / rpm)
        else -> base
    }
}