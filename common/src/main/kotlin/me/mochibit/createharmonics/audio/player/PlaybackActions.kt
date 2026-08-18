package me.mochibit.createharmonics.audio.player

interface PlaybackActions {
    // query actions
    fun hasActiveSoundInstance(): Boolean

    fun isSeekingDisabled(): Boolean

    fun currentPlaytime(): Double

    fun isSameSourceAsCurrentRequest(request: AudioRequest): Boolean

    fun isValidGeneration(streamGeneration: Int): Boolean

    // state mutator actions
    fun setCurrentRequest(request: AudioRequest)

    fun resetRetrySchedule()

    fun cancelPendingRetry()

    // playback control
    suspend fun cancelLoading()

    suspend fun startPlayback(position: Double)

    suspend fun pausePlayback()

    suspend fun resumePlayback()

    suspend fun stopPlayback(isSeek: Boolean = false)

    suspend fun unstuckPlayback()

    suspend fun failPlayback(
        shouldDisableSeek: Boolean = false,
        shouldRetry: Boolean = false,
    )

    suspend fun applyStreamReady(intent: PlayerIntent.StreamReady)

    suspend fun notifyAudioFinished()
}