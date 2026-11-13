package me.mochibit.createharmonics.foundation.signals

import java.util.concurrent.ConcurrentHashMap

/**
 * Signal box
 *
 * @param K
 * @constructor Creates a new SignalBox
 */
class SignalBox<K> {
    private val signals = ConcurrentHashMap.newKeySet<K>()

    /**
     * Plus assign
     *
     * @param key
     */
    operator fun plusAssign(key: K) {
        signals.add(key)
    }

    /**
     * Receives
     *
     * @param key
     * @return `true` if the condition holds, `false` otherwise
     */
    fun consume(key: K): Boolean = signals.remove(key)

    /**
     * Minus assign
     *
     * @param key
     */
    operator fun minusAssign(key: K) {
        signals.remove(key)
    }
}

/**
 * Message box
 *
 * @param K
 * @param M
 * @constructor Creates a new MessageBox
 */
class MessageBox<K : Any, M : Any> {
    private val pending = ConcurrentHashMap<K, M>()

    /**
     * Sets
     *
     * @param key
     * @param message
     */
    operator fun set(
        key: K,
        message: M,
    ) {
        pending[key] = message
    }

    /**
     * Receives
     *
     * @param key
     * @return the m
     */
    fun consume(key: K): M? = pending.remove(key)
}
