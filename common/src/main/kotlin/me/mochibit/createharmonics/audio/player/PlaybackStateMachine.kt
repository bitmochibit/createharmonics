package me.mochibit.createharmonics.audio.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.mochibit.createharmonics.config.ClientConfig
import me.mochibit.createharmonics.foundation.debug
import me.mochibit.createharmonics.foundation.info


class PlaybackStateMachine(
    private val scope: CoroutineScope,
    private val actions: PlaybackActions,
) {
    private val intents = Channel<PlayerIntent>(Channel.UNLIMITED)

    private val _state = MutableStateFlow(PlayerState.STOPPED)
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    fun send(intent: PlayerIntent) = intents.trySend(intent)

    fun shutdown() = intents.close()

    fun start(): Job =
        scope.launch(Dispatchers.IO) {
            try {
                for (intent in intents) {
                    val stateBefore = _state.value
                    try {
                        handle(intent)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        if (ClientConfig.debugAudioPlayer.get()) {
                            "[AUDIO PLAYER] Intent $intent failed while in state $stateBefore: ${e.message}".debug()
                        }
                        e.printStackTrace()
                    }
                    if (ClientConfig.debugAudioPlayer.get()) {
                        "[AUDIO PLAYER] $intent: $stateBefore -> ${_state.value}".debug()
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    intents.close()
                    actions.stopPlayback()
                }
                scope.cancel()
            }
        }

    fun transition(next: PlayerState) {
        val current = _state.value
        check(current.canTransitionTo(next)) { "Invalid transition: $current → $next" }
        _state.value = next
    }

    fun forceState(next: PlayerState) {
        _state.value = next
    }

    private suspend fun handle(intent: PlayerIntent) {
        when (intent) {
            is PlayerIntent.Play -> handlePlay(intent)
            is PlayerIntent.Pause -> handlePause()
            is PlayerIntent.Stop -> handleStop()
            is PlayerIntent.Seek -> handleSeek(intent)
            is PlayerIntent.StreamReady -> handleStreamReady(intent)
            is PlayerIntent.StreamFailed -> actions.failPlayback(intent.shouldDisableSeek, intent.shouldRetry)
            is PlayerIntent.AudioFinished -> handleAudioFinished()
            is PlayerIntent.AudioHanged -> handleAudioHanged()
            is PlayerIntent.NewRequest -> handleNewRequest(intent)
            else -> "Invalid intent sent to the audio player!".info()
        }
    }

    private suspend fun handlePlay(intent: PlayerIntent.Play) {
        actions.resetRetrySchedule()
        when (_state.value) {
            PlayerState.PLAYING, PlayerState.LOADING -> return

            PlayerState.PAUSED -> {
                if (actions.hasActiveSoundInstance()) {
                    actions.resumePlayback()
                } else {
                    actions.stopPlayback()
                    actions.startPlayback(actions.currentPlaytime())
                }
            }

            PlayerState.STOPPED -> actions.startPlayback(intent.initialPosition)
        }
    }

    private suspend fun handlePause() {
        when (_state.value) {
            PlayerState.PAUSED -> return

            PlayerState.LOADING -> {
                actions.cancelLoading()
                actions.pausePlayback()
            }

            PlayerState.PLAYING -> actions.pausePlayback()

            else -> return
        }
    }

    private suspend fun handleStop() {
        if (_state.value == PlayerState.STOPPED) return
        actions.cancelPendingRetry()
        actions.stopPlayback()
    }

    private suspend fun handleSeek(intent: PlayerIntent.Seek) {
        actions.cancelLoading()
        if (actions.isSeekingDisabled()) return
        if (_state.value == PlayerState.PLAYING || _state.value == PlayerState.PAUSED) {
            actions.stopPlayback(isSeek = true)
            actions.startPlayback(intent.position)
        }
    }

    private suspend fun handleStreamReady(intent: PlayerIntent.StreamReady) {
        if (_state.value != PlayerState.LOADING || !actions.isValidGeneration(intent.streamGeneration)) {
            intent.stream.close()
            return
        }
        actions.applyStreamReady(intent)
        transition(PlayerState.PLAYING)
    }

    private suspend fun handleAudioFinished() {
        actions.cancelLoading()
        actions.notifyAudioFinished()
        actions.stopPlayback()
    }

    private suspend fun handleAudioHanged() {
        actions.cancelLoading()
        when (_state.value) {
            PlayerState.PAUSED -> {
                actions.resumePlayback()
                actions.pausePlayback()
            }

            PlayerState.PLAYING -> actions.unstuckPlayback()

            else -> return
        }
    }

    private suspend fun handleNewRequest(intent: PlayerIntent.NewRequest) {
        if (actions.isSameSourceAsCurrentRequest(intent.req)) return

        actions.cancelLoading()
        if (_state.value == PlayerState.PLAYING || _state.value == PlayerState.LOADING) return

        actions.resetRetrySchedule()
        actions.stopPlayback()
        actions.setCurrentRequest(intent.req)
    }
}