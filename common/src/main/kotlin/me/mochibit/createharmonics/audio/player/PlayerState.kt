package me.mochibit.createharmonics.audio.player

/**
 * Player state
 * Enum — a fixed set of named constants.
 *
 * @constructor Creates a new PlayerState
 */
enum class PlayerState {
    /**
     * Stopped
     *
     * @constructor Creates a new STOPPED
     */
    STOPPED,

    /**
     * Loading
     *
     * @constructor Creates a new LOADING
     */
    LOADING,

    /**
     * Playing
     *
     * @constructor Creates a new PLAYING
     */
    PLAYING,

    /**
     * Paused
     *
     * @constructor Creates a new PAUSED
     */
    PAUSED
    ;

    /**
     * Returns `true` if transition to
     *
     * @param next
     * @return `true` if the condition holds, `false` otherwise
     */
    fun canTransitionTo(next: PlayerState): Boolean =
        when (this) {
            STOPPED -> next in setOf(LOADING)
            LOADING -> next in setOf(PLAYING, PAUSED, STOPPED, LOADING)
            PLAYING -> next in setOf(PAUSED, STOPPED, LOADING)
            PAUSED -> next in setOf(PLAYING, STOPPED, LOADING)
        }
}
