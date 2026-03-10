package me.mochibit.createharmonics.audio.player

enum class PlayerState {
    LOADING,
    PLAYING,
    PAUSED,
    STOPPED,
    HANGED,
    FINISHING,
    ;

    fun canTransitionTo(next: PlayerState): Boolean =
        when (this) {
            STOPPED -> next == LOADING
            LOADING -> next == PLAYING || next == STOPPED || next == FINISHING
            PLAYING -> next == PAUSED || next == STOPPED || next == FINISHING || next == HANGED
            PAUSED -> next == PLAYING || next == STOPPED
            FINISHING -> true
            HANGED -> true
        }
}
