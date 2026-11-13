package me.mochibit.createharmonics.audio.upload

/**
 * Server audio reference
 * Data class — a value holder; `equals`, `hashCode`, `toString` and `copy` are generated.
 *
 * @constructor Creates a new ServerAudioReference
 * @property owner
 * @property fileId the file id
 */
data class ServerAudioReference(val owner: String, val fileId: String) {
    override fun toString() = "$PREFIX$owner/$fileId"

    /**
     * Companion
     * Companion object — holds factory methods and constants for the enclosing class.
     *
     * @constructor Creates a new Companion
     */
    companion object {
        const val PREFIX = "createharmonics://audio/"

        /**
         * Converts
         *
         * @param url
         * @return the server audio reference
         */
        fun parse(url: String): ServerAudioReference? {
            if (!url.startsWith(PREFIX)) return null
            val parts = url.removePrefix(PREFIX).split('/')
            return if (parts.size == 2 && parts.none { it.isBlank() }) ServerAudioReference(parts[0], parts[1]) else null
        }
    }
}