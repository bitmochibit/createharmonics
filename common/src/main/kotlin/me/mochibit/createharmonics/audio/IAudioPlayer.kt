package me.mochibit.createharmonics.audio

import kotlinx.coroutines.flow.StateFlow
import me.mochibit.createharmonics.audio.comp.SoundEventComposition
import me.mochibit.createharmonics.audio.effect.EffectChain
import java.io.InputStream

interface IAudioPlayer {
    val playerId: String
    val state: AudioPlayerState
    val stateFlow: StateFlow<AudioPlayerState>

    val currentEffectChain: EffectChain?

    fun play(
        url: String,
        effectChain: EffectChain = EffectChain.Companion.empty(),
        composition: SoundEventComposition = SoundEventComposition(),
        offsetSeconds: Double = 0.0,
    ): Any

    fun playFromStream(
        inputStream: InputStream,
        label: String = "stream",
        effectChain: EffectChain = EffectChain.Companion.empty(),
        composition: SoundEventComposition = SoundEventComposition(),
        offsetSeconds: Double = 0.0,
        sampleRateOverride: Int? = null,
    ): Any

    fun pause(): Any

    fun resume(): Any

    fun stop(): Any

    fun dispose(): Any
}
