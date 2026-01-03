package me.mochibit.createharmonics.network.packet

import com.simibubi.create.foundation.networking.BlockEntityConfigurationPacket
import me.mochibit.createharmonics.content.processing.recordPressBase.RecordPressBaseBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import kotlin.properties.Delegates

class ConfigureRecordPressBasePacket : BlockEntityConfigurationPacket<RecordPressBaseBlockEntity> {
    private lateinit var audioUrls: MutableList<String>
    private var randomMode: Boolean = false

    constructor(pos: BlockPos, audioUrls: List<String>, randomMode: Boolean) : super(pos) {
        this.audioUrls = audioUrls.toMutableList()
        this.randomMode = randomMode
    }

    constructor(buffer: FriendlyByteBuf) : super(buffer)

    override fun writeSettings(buffer: FriendlyByteBuf) {
        buffer.writeInt(audioUrls.size)
        for (url in audioUrls) {
            buffer.writeUtf(url)
        }
        buffer.writeBoolean(randomMode)
    }

    override fun readSettings(buffer: FriendlyByteBuf) {
        val size = buffer.readInt()
        audioUrls = mutableListOf()
        repeat(size) {
            audioUrls.add(buffer.readUtf())
        }
        randomMode = buffer.readBoolean()
    }

    override fun applySettings(be: RecordPressBaseBlockEntity) {
        be.audioUrls = audioUrls
        be.randomMode = randomMode
    }
}
