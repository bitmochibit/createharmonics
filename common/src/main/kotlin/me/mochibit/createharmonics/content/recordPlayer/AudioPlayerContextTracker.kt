package me.mochibit.createharmonics.content.recordPlayer

abstract class AudioPlayerContextTracker<Tracked> {
    private val _trackedPlayerContexts = mutableMapOf<String, Tracked>()
    val trackedPlayerContexts: Map<String, Tracked> get() = _trackedPlayerContexts

    fun track(
        playerId: String,
        tracked: Tracked,
    ) {
        _trackedPlayerContexts.putIfAbsent(playerId, tracked)
    }

    fun untrack(playerId: String) {
        _trackedPlayerContexts.remove(playerId)
    }

    fun get(playerId: String): Tracked? = _trackedPlayerContexts[playerId]
}
