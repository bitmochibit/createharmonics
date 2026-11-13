package me.mochibit.createharmonics.foundation.extension

import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * In ticks on the receiver [Duration]
 *
 * @return the int
 */
fun Duration.inTicks(): Int = (this.inWholeMilliseconds / 50).toInt()

/**
 * Ticks on the receiver [Int]
 *
 * @return the duration
 */
fun Int.ticks(): Duration = this.toDuration(DurationUnit.MILLISECONDS) * 50
