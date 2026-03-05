package me.mochibit.createharmonics.audio

sealed class AudioPlayerState {
    data object Stopped : AudioPlayerState()

    data object Loading : AudioPlayerState()

    data object Playing : AudioPlayerState()

    data object Paused : AudioPlayerState()

    data class Error(
        val cause: Throwable,
    ) : AudioPlayerState()

    fun canPlay() = this is Stopped || this is Error

    fun canPause() = this is Playing

    fun canResume() = this is Paused

    fun canStop() = this !is Stopped
}
