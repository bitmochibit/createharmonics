package me.mochibit.createharmonics.audio.player

enum class PlayerState {
    STOPPED,
    LOADING,
    PLAYING,
    PAUSED,
    TAILING,
    HANGED,
    ;

    fun canTransitionTo(next: PlayerState): Boolean =
        when (this) {
            STOPPED -> next in setOf(LOADING)
            LOADING -> next in setOf(PLAYING, PAUSED, STOPPED, LOADING)
            PLAYING -> next in setOf(PAUSED, STOPPED, TAILING, LOADING, HANGED)
            PAUSED -> next in setOf(PLAYING, STOPPED, TAILING, LOADING)
            TAILING -> next in setOf(STOPPED, LOADING)
            HANGED -> next in setOf(PLAYING, STOPPED)
        }
}
