package me.mochibit.createharmonics.audio.effect

import net.minecraft.client.sounds.AudioStream
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToInt

/**
 * Mixer effect
 *
 * @constructor Creates a new MixerEffect
 * @property mixName the mix name
 * @property scope
 */
class MixerEffect(
    val mixName: String = "DefaultMixName",
    override val scope: AudioEffect.Scope = AudioEffect.Scope.PERMANENT,
) : AudioEffect {
    /**
     * Mixed source
     * Data class — a value holder; `equals`, `hashCode`, `toString` and `copy` are generated.
     *
     * @constructor Creates a new MixedSource
     * @property id
     * @property stream
     * @property level
     * @property autoRemoveOnExhaust the auto remove on exhaust
     */
    data class MixedSource(
        val id: String,
        val stream: AudioStream,
        val level: Float = 0.5f,
        val autoRemoveOnExhaust: Boolean = true,
    )

    private val sources = CopyOnWriteArrayList<MixedSource>()

    /**
     * Adds the source
     *
     * @param id
     * @param stream
     * @param level
     * @param autoRemoveOnExhaust the auto remove on exhaust
     */
    fun addSource(
        id: String,
        stream: AudioStream,
        level: Float = 0.5f,
        autoRemoveOnExhaust: Boolean = true,
    ) {
        sources.removeIf { it.id == id }
        sources.add(MixedSource(id, stream, level, autoRemoveOnExhaust))
    }

    /**
     * Removes the source
     *
     * @param id
     */
    fun removeSource(id: String) {
        val removed = sources.filter { it.id == id }
        sources.removeIf { it.id == id }
        removed.forEach { runCatching { it.stream.close() } }
    }

    /**
     * Removes the sources
     *
     */
    fun clearSources() {
        val snapshot = sources.toList()
        sources.clear()
        snapshot.forEach { runCatching { it.stream.close() } }
    }

    /**
     * Returns `true` if sources
     *
     * @return `true` if the condition holds, `false` otherwise
     */
    fun hasSources(): Boolean = sources.isNotEmpty()

    override fun process(
        samples: ShortArray,
        timeInSeconds: Double,
        sampleRate: Int,
    ): ShortArray {
        if (sources.isEmpty()) return samples

        val output = samples.copyOf()
        val exhaustedIds = mutableListOf<String>()

        for (source in sources) {
            val (secondary, exhausted) = readStream(source.stream, samples.size)
            if (exhausted && source.autoRemoveOnExhaust) {
                exhaustedIds += source.id
                continue
            }

            val primaryLevel = 1f - source.level.coerceIn(0f, 1f)
            val secondaryLevel = source.level.coerceIn(0f, 1f)

            for (i in output.indices) {
                val mixed = output[i] * primaryLevel + secondary[i] * secondaryLevel
                output[i] = mixed.roundToInt().coerceIn(-32768, 32767).toShort()
            }
        }

        if (exhaustedIds.isNotEmpty()) {
            val exhausted = sources.filter { it.id in exhaustedIds }
            sources.removeIf { it.id in exhaustedIds }
            exhausted.forEach { runCatching { it.stream.close() } }
        }

        return output
    }

    private fun readStream(
        stream: AudioStream,
        sampleCount: Int,
    ): Pair<ShortArray, Boolean> {
        val buffer: ByteBuffer = stream.read(sampleCount * 2)
        val bytesAvailable = buffer.remaining()

        if (bytesAvailable == 0) return ShortArray(sampleCount) to true

        val samplesAvailable = bytesAvailable / 2
        val bytes = ByteArray(bytesAvailable).also { buffer.get(it) }
        val result =
            ShortArray(sampleCount) { i ->
                if (i >= samplesAvailable) return@ShortArray 0
                val lo = bytes[i * 2].toInt() and 0xFF
                val hi = bytes[i * 2 + 1].toInt() and 0xFF
                ((hi shl 8) or lo).toShort()
            }
        return result to false
    }

    override fun reset() = clearSources()

    override fun getName() = mixName
}
