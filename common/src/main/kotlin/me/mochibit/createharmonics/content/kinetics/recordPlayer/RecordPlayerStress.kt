package me.mochibit.createharmonics.content.kinetics.recordPlayer

/**
 * Record player stress
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new RecordPlayerStress
 */
object RecordPlayerStress {
    /**
     * Impact
     *
     * @param base
     * @param state
     * @param speedInterrupted the speed interrupted
     * @param staticPitch the static pitch
     * @param rpm
     * @return the float
     */
    fun impact(base: Float, state: PlaybackState, speedInterrupted: Boolean, staticPitch: Boolean, rpm: Float) = when {
        state == PlaybackState.STOPPED -> 0f
        state == PlaybackState.PAUSED && !speedInterrupted -> base / 4f
        staticPitch && rpm > 0f && rpm <= PitchCurve.UNITY_RPM -> base * (PitchCurve.UNITY_RPM / rpm)
        else -> base
    }
}