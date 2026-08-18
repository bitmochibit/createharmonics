package me.mochibit.createharmonics.audio.player

enum class PlayerState {
    STOPPED,
    LOADING,
    PLAYING,
    PAUSED
    ;

    fun canTransitionTo(next: PlayerState): Boolean =
        when (this) {
            STOPPED -> next in setOf(LOADING)
            LOADING -> next in setOf(PLAYING, PAUSED, STOPPED, LOADING)
            PLAYING -> next in setOf(PAUSED, STOPPED, LOADING)
            PAUSED -> next in setOf(PLAYING, STOPPED, LOADING)
        }
}
