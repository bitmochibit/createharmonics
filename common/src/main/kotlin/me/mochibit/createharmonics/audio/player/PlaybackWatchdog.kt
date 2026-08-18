package me.mochibit.createharmonics.audio.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.minecraft.client.resources.sounds.SoundInstance
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


class PlaybackWatchdog(
    private val scope: CoroutineScope,
    private val interval: Duration = 1.seconds,
    private val isPlaying: () -> Boolean,
    private val currentInstance: () -> SoundInstance?,
    private val isActiveInSoundManager: (SoundInstance) -> Boolean,
    private val onHang: () -> Unit,
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job =
            scope.launch(Dispatchers.Default) {
                while (isActive) {
                    delay(interval)
                    val instance = currentInstance() ?: continue
                    if (isPlaying() && !isActiveInSoundManager(instance)) onHang()
                }
            }
    }

    fun stop() = job?.cancel()
}

