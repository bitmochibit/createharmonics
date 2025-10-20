package me.mochibit.createharmonics.audio.pcm

/**
 * Utility functions for PCM audio data conversion.
 * Handles conversion between byte arrays and 16-bit PCM samples.
 */
object PCMUtils {
    /**
     * Convert byte array to 16-bit PCM samples (little-endian)
     */
    fun bytesToShorts(bytes: ByteArray): ShortArray {
        val shorts = ShortArray(bytes.size / 2)
        for (i in shorts.indices) {
            val offset = i * 2
            shorts[i] = ((bytes[offset + 1].toInt() and 0xFF) shl 8 or
                    (bytes[offset].toInt() and 0xFF)).toShort()
        }
        return shorts
    }

    /**
     * Convert 16-bit PCM samples to byte array (little-endian)
     */
    fun shortsToBytes(shorts: ShortArray): ByteArray {
        val bytes = ByteArray(shorts.size * 2)
        for (i in shorts.indices) {
            val offset = i * 2
            bytes[offset] = (shorts[i].toInt() and 0xFF).toByte()
            bytes[offset + 1] = ((shorts[i].toInt() shr 8) and 0xFF).toByte()
        }
        return bytes
    }
}