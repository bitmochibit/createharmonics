package me.mochibit.createharmonics.content.recordPlayer

abstract class AudioPlayerContextTracker<Tracked> {
    private val trackedPlayerContexts = mutableMapOf<String, Tracked>()

    fun track(
        playerId: String,
        tracked: Tracked,
    ) {
        if (trackedPlayerContexts.containsKey(playerId)) return
        trackedPlayerContexts[playerId] = tracked
    }

    fun untrack(playerId: String) {
        trackedPlayerContexts.remove(playerId)
    }

    fun get(playerId: String): Tracked? = trackedPlayerContexts[playerId]
}
