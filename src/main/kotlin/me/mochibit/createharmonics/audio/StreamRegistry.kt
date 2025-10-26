package me.mochibit.createharmonics.audio

import me.mochibit.createharmonics.Logger.info
import net.minecraft.resources.ResourceLocation
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry that maps ResourceLocations to their corresponding audio streams.
 * Thread-safe and handles stream lifecycle management.
 */
object StreamRegistry {
    private val streams = ConcurrentHashMap<ResourceLocation, InputStream>()

    /**
     * Register a stream for a given resource location.
     */
    fun registerStream(location: ResourceLocation, stream: InputStream) {
        streams[location] = stream
        info("StreamRegistry: Registered stream for $location")
    }

    /**
     * Get a stream for a given resource location.
     */
    fun getStream(location: ResourceLocation): InputStream? {
        return streams[location]
    }

    /**
     * Unregister a stream when it's no longer needed.
     * This actually stops the stream and cleans up resources.
     */
    fun unregisterStream(location: ResourceLocation) {
        streams.remove(location)?.let { stream ->
            info("StreamRegistry: Unregistered stream for $location")
            try {
                if (stream is BufferedAudioStream) {
                    stream.destroy()
                } else {
                    stream.close()
                }
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
