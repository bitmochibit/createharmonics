package me.mochibit.createharmonics.network.packet

import com.simibubi.create.foundation.networking.BlockEntityConfigurationPacket
import me.mochibit.createharmonics.content.processing.recordPressBase.RecordPressBaseBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf

class ConfigureRecordPressBasePacket : BlockEntityConfigurationPacket<RecordPressBaseBlockEntity> {
    private lateinit var audioUrl: String
    private lateinit var recordName: String

    constructor(pos: BlockPos, audioUrl: String, recordName: String = "") : super(pos) {
        this.audioUrl = audioUrl
        this.recordName = recordName
    }

    constructor(buffer: FriendlyByteBuf) : super(buffer)

    override fun writeSettings(buffer: FriendlyByteBuf) {
        buffer.writeUtf(audioUrl)
        buffer.writeUtf(recordName)
    }

    override fun readSettings(buffer: FriendlyByteBuf) {
        audioUrl = buffer.readUtf()
        recordName = buffer.readUtf()
    }

    override fun applySettings(be: RecordPressBaseBlockEntity) {
        be.urlTemplate = audioUrl
    }
}
