package me.mochibit.createharmonics.content.processing.recordPressBase

import kotlinx.serialization.Serializable

@Serializable
data class RecordPressBaseConfiguration(
    /**
     * True for random selection, false for sequential
     */
    val randomMode: Boolean = false,
    val currentUrlIndex: Int = 0,
    val entries: List<AudioEntry> = emptyList(),
) {
    @Serializable
    data class AudioEntry(val url: String = "", val weight: Float = 1f, val name: String? = null)
}