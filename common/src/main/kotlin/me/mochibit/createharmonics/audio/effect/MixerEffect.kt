package me.mochibit.createharmonics.audio.effect

import net.minecraft.client.sounds.AudioStream
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToInt

class MixerEffect(
    val mixName: String = "DefaultMixName",
    override val scope: AudioEffect.Scope = AudioEffect.Scope.PERMANENT,
) : AudioEffect {
    /**
     * Mixed source to be played along the main source
     * If the source is not a ONE-SHOT and must be kept playing, set [autoRemoveOnExhaust] to false
     */
    data class MixedSource(
        val id: String,
        val stream: AudioStream,
        val level: Float = 0.5f,
        val autoRemoveOnExhaust: Boolean = true,
    )

    private val sources = CopyOnWriteArrayList<MixedSource>()

    fun addSource(
        id: String,
        stream: AudioStream,
        level: Float = 0.5f,
        autoRemoveOnExhaust: Boolean = true,
    ) {
        sources.removeIf { it.id == id }
        sources.add(MixedSource(id, stream, level, autoRemoveOnExhaust))
    }

    fun removeSource(id: String) {
        val removed = sources.filter { it.id == id }
        sources.removeIf { it.id == id }
        removed.forEach { runCatching { it.stream.close() } }
    }

    fun clearSources() {
        val snapshot = sources.toList()
        sources.clear()
        snapshot.forEach { runCatching { it.stream.close() } }
    }

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
