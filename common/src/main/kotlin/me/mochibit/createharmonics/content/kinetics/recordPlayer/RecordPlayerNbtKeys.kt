package me.mochibit.createharmonics.content.kinetics.recordPlayer

/**
 * Record player nbt keys
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new RecordPlayerNbtKeys
 */
object RecordPlayerNbtKeys {
    const val UUID = "RecordPlayerUUID"
    const val SCROLL_VALUE = "ScrollValue"
    const val REDSTONE_POWER = "RedstonePower"
    const val PLAYBACK_STATE = "PlaybackState"
    const val PLAYBACK_STATE_MOVING = "PlaybackStateMoving"
    const val CLOCK_OFFSET = "ClockOffset"
    const val HELD_RECORD = "HeldRecordItem"
}