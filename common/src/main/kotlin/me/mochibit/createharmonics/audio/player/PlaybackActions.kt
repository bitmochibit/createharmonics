package me.mochibit.createharmonics.audio.player

/**
 * Playback actions
 *
 */
interface PlaybackActions {
    /**
     * Returns `true` if active sound instance
     *
     * @return `true` if the condition holds, `false` otherwise
     */// query actions
    fun hasActiveSoundInstance(): Boolean

    /**
     * Returns `true` if seeking disabled
     *
     * @return `true` if the condition holds, `false` otherwise
     */
    fun isSeekingDisabled(): Boolean

    /**
     * Current playtime
     *
     * @return the double
     */
    fun currentPlaytime(): Double

    /**
     * Returns `true` if same source as current request
     *
     * @param request
     * @return `true` if the condition holds, `false` otherwise
     */
    fun isSameSourceAsCurrentRequest(request: AudioRequest): Boolean

    /**
     * Returns `true` if valid generation
     *
     * @param streamGeneration the stream generation
     * @return `true` if the condition holds, `false` otherwise
     */
    fun isValidGeneration(streamGeneration: Int): Boolean

    /**
     * Sets the current request
     *
     * @param request
     */// state mutator actions
    fun setCurrentRequest(request: AudioRequest)

    /**
     * Resets the retry schedule
     *
     */
    fun resetRetrySchedule()

    /**
     * Stops the pending retry
     *
     */
    fun cancelPendingRetry()

    /**
     * Stops the loading
     * Suspending function — must be called from a coroutine or another suspending function.
     *
     */// playback control
    suspend fun cancelLoading()

    /**
     * Starts the playback
     * Suspending function — must be called from a coroutine or another suspending function.
     *
     * @param position
     */
    suspend fun startPlayback(position: Double)

    /**
     * Pause playback
     * Suspending function — must be called from a coroutine or another suspending function.
     *
     */
    suspend fun pausePlayback()

    /**
     * Resume playback
     * Suspending function — must be called from a coroutine or another suspending function.
     *
     */
    suspend fun resumePlayback()

    /**
     * Stops the playback
     * Suspending function — must be called from a coroutine or another suspending function.
     *
     * @param isSeek the is seek
     */
    suspend fun stopPlayback(isSeek: Boolean = false)

    /**
     * Unstuck playback
     * Suspending function — must be called from a coroutine or another suspending function.
     *
     */
    suspend fun unstuckPlayback()

    /**
     * Fail playback
     * Suspending function — must be called from a coroutine or another suspending function.
     *
     * @param shouldDisableSeek the should disable seek
     * @param shouldRetry the should retry
     */
    suspend fun failPlayback(
        shouldDisableSeek: Boolean = false,
        shouldRetry: Boolean = false,
    )

    /**
     * Handles the stream ready
     * Suspending function — must be called from a coroutine or another suspending function.
     *
     * @param intent
     */
    suspend fun applyStreamReady(intent: PlayerIntent.StreamReady)

    /**
     * Sends the audio finished
     * Suspending function — must be called from a coroutine or another suspending function.
     *
     */
    suspend fun notifyAudioFinished()
}