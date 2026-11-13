package me.mochibit.createharmonics.content.processing.recordPressBase

import kotlinx.serialization.Serializable

/**
 * Record press base configuration
 * Data class — a value holder; `equals`, `hashCode`, `toString` and `copy` are generated.
 *
 * @constructor Creates a new RecordPressBaseConfiguration
 * @property randomMode the random mode
 * @property currentUrlIndex the current url index
 * @property entries
 */
@Serializable
data class RecordPressBaseConfiguration(
    /**
     * True for random selection, false for sequential
     */
    val randomMode: Boolean = false,
    val currentUrlIndex: Int = 0,
    val entries: List<AudioEntry> = emptyList(),
) {
    /**
     * Audio entry
     * Data class — a value holder; `equals`, `hashCode`, `toString` and `copy` are generated.
     *
     * @constructor Creates a new AudioEntry
     * @property url
     * @property weight
     * @property name
     */
    @Serializable
    data class AudioEntry(val url: String = "", val weight: Float = 1f, val name: String? = null)
}