package me.mochibit.createharmonics.content.block.recordPlayer

enum class PlaybackState {
    PLAYING,
    STOPPED,
    PAUSED;

    companion object {
        fun fromOrdinal(ordinal: Int): PlaybackState {
            return entries.getOrNull(ordinal) ?: STOPPED
        }
    }
}