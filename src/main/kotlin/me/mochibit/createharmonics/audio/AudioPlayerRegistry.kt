package me.mochibit.createharmonics.audio


import java.util.concurrent.ConcurrentHashMap

object AudioPlayerRegistry {
    private val players = ConcurrentHashMap<String, AudioPlayer>()

    fun registerPlayer(id: String, player: AudioPlayer) {
        players[id] = player
    }

    fun getOrCreatePlayer(id: String, factory: () -> AudioPlayer): AudioPlayer {
        return players.getOrPut(id) { factory() }
    }


    fun getPlayer(id: String): AudioPlayer? {
        return players[id]
    }

    fun destroyPlayer(id: String) {
        players.remove(id)?.dispose()
    }

    fun clear() {
        (players.keys).forEach { destroyPlayer(it) }
    }

    fun containsStream(id: String): Boolean {
        return players.containsKey(id)
    }
}
