package me.mochibit.createharmonics.audio.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.mochibit.createharmonics.foundation.info
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds



class RetryScheduler(
    private val scope: CoroutineScope,
    private val baseDelay: Duration = 1.seconds,
    private val maxDelay: Duration = 30.seconds,
    private val maxAttempts: Int = 6,
    private val onRetry: suspend () -> Unit,
) {
    private val attempt = AtomicInteger(0)

    @Volatile
    private var pendingJob: Job? = null

    val isRetryPending: Boolean
        get() = pendingJob?.isActive == true

    fun reset() {
        cancelPending()
        attempt.set(0)
    }

    fun cancelPending() {
        pendingJob?.cancel()
        pendingJob = null
    }

    fun scheduleRetry() {
        val currentAttempt = attempt.getAndIncrement()
        if (currentAttempt >= maxAttempts) {
            "Retry limit reached ($maxAttempts attempts), giving up.".info()
            return
        }

        val delayDuration = nextDelay(currentAttempt)
        pendingJob = scope.launch(Dispatchers.Default) {
            delay(delayDuration)
            onRetry()
        }
    }

    private fun nextDelay(attempt: Int): Duration {
        val growthFactor = 1 shl min(attempt, 5)
        val exponential = baseDelay * growthFactor
        val withJitter = exponential * Random.nextDouble(0.8, 1.2)
        return withJitter.coerceAtMost(maxDelay)
    }
}


