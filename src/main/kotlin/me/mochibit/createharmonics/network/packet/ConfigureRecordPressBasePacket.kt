package me.mochibit.createharmonics.network.packet

import com.simibubi.create.foundation.networking.BlockEntityConfigurationPacket
import me.mochibit.createharmonics.content.processing.recordPressBase.RecordPressBaseBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import kotlin.properties.Delegates

class ConfigureRecordPressBasePacket : BlockEntityConfigurationPacket<RecordPressBaseBlockEntity> {
    private lateinit var audioUrls: MutableList<String>
    private lateinit var urlWeights: MutableList<Float>
    private var randomMode: Boolean = false
    private var newIndex: Int = 0

    constructor(
        pos: BlockPos,
        audioUrls: List<String>,
        urlWeights: List<Float>,
        randomMode: Boolean,
        newIndex: Int,
    ) : super(pos) {
        this.audioUrls = audioUrls.toMutableList()
        this.urlWeights = urlWeights.toMutableList()
        this.randomMode = randomMode
        this.newIndex = newIndex
    }

    constructor(buffer: FriendlyByteBuf) : super(buffer)

    override fun writeSettings(buffer: FriendlyByteBuf) {
        buffer.writeInt(audioUrls.size)
        for (url in audioUrls) {
            buffer.writeUtf(url)
        }
        buffer.writeInt(urlWeights.size)
        for (weight in urlWeights) {
            buffer.writeFloat(weight)
        }
        buffer.writeBoolean(randomMode)
        buffer.writeInt(newIndex)
    }

    override fun readSettings(buffer: FriendlyByteBuf) {
        val size = buffer.readInt()
        audioUrls = mutableListOf()
        repeat(size) {
            audioUrls.add(buffer.readUtf())
        }
        val weightsSize = buffer.readInt()
        urlWeights = mutableListOf()
        repeat(weightsSize) {
            urlWeights.add(buffer.readFloat())
        }
        randomMode = buffer.readBoolean()
        newIndex = buffer.readInt()
    }

    override fun applySettings(be: RecordPressBaseBlockEntity) {
        be.audioUrls = audioUrls
        be.urlWeights = urlWeights
        be.randomMode = randomMode
        be.currentUrlIndex = newIndex
    }
}
