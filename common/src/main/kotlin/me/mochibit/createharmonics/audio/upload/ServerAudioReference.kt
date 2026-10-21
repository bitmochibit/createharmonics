package me.mochibit.createharmonics.audio.upload

data class ServerAudioReference(val owner: String, val fileId: String) {
    override fun toString() = "$PREFIX$owner/$fileId"

    companion object {
        const val PREFIX = "createharmonics://audio/"

        fun parse(url: String): ServerAudioReference? {
            if (!url.startsWith(PREFIX)) return null
            val parts = url.removePrefix(PREFIX).split('/')
            return if (parts.size == 2 && parts.none { it.isBlank() }) ServerAudioReference(parts[0], parts[1]) else null
        }
    }
}