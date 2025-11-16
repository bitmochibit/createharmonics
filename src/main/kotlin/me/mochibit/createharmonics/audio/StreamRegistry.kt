package me.mochibit.createharmonics.audio

import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

object StreamRegistry {
    private val streams = ConcurrentHashMap<String, InputStream>()


    fun registerStream(id: String, stream: InputStream) {
        streams[id] = stream
    }

    fun containsStream(id: String): Boolean {
        return streams.containsKey(id)
    }

    /**
     * Get a stream for a given resource location.
     */
    fun getStream(id: String): InputStream? {
        return streams[id]
    }

    /**
     * Unregister a stream when it's no longer needed.
     * This actually stops the stream and cleans up resources.
     */
    fun unregisterStream(id: String) {
        streams.remove(id)?.let { stream ->
            try {
                stream.close()
            } catch (e: Exception) {
            }
        }
    }

    /**
     * Clear all registered streams.
     */
    fun clear() {
        streams.keys.toList().forEach { unregisterStream(it) }
    }
}
