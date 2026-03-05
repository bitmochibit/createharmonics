package me.mochibit.createharmonics.audio

import me.mochibit.createharmonics.audio.effect.EffectChain

fun interface OnStreamEnd {
    fun invoke(
        playerId: String,
        failed: Boolean,
    )
}

fun interface OnStateChange {
    fun invoke(
        playerId: String,
        prev: AudioPlayerState,
        curr: AudioPlayerState,
    )
}

fun interface OnEffectChainReady {
    fun invoke(chain: EffectChain)
}

data class AudioPlayerCallbacks(
    val onStreamEnd: OnStreamEnd? = null,
    val onStateChange: OnStateChange? = null,
    val onEffectChainReady: OnEffectChainReady? = null,
)
