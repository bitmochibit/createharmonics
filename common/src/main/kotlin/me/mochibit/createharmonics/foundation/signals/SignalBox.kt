package me.mochibit.createharmonics.foundation.signals

import java.util.concurrent.ConcurrentHashMap

class SignalBox<K> {
    private val signals = ConcurrentHashMap.newKeySet<K>()

    operator fun plusAssign(key: K) {
        signals.add(key)
    }

    fun consume(key: K): Boolean = signals.remove(key)

    operator fun minusAssign(key: K) {
        signals.remove(key)
    }
}

class MessageBox<K : Any, M : Any> {
    private val pending = ConcurrentHashMap<K, M>()

    operator fun set(
        key: K,
        message: M,
    ) {
        pending[key] = message
    }

    fun consume(key: K): M? = pending.remove(key)
}
