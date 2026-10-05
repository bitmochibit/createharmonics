package me.mochibit.createharmonics.foundation.network.packet

import com.simibubi.create.foundation.utility.AdventureUtil
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import me.mochibit.createharmonics.content.processing.recordPressBase.RecordPressBaseBlockEntity
import net.minecraft.core.BlockPos

//TODO serialize this in a proper datastructure
@AutoPacket
@Serializable
class ConfigureRecordPressBasePacket(
    @Contextual val blockPos: BlockPos,
    val audioUrls: MutableList<String>,
    val urlWeights: MutableList<Float>,
    val randomMode: Boolean,
    val newIndex: Int,
) : C2SPacket {
    override fun handle(context: ServerPacketContext) {
        val sender = context.player
        if (sender.isSpectator || AdventureUtil.isAdventure(sender)) return
        val world = sender.level()
        if (world == null || !world.isLoaded(blockPos)) return
        if (!sender.canInteractWithBlock(blockPos, 20.0)) return
        val blockEntity = world.getBlockEntity(blockPos)
        if (blockEntity is RecordPressBaseBlockEntity) {
            blockEntity.audioUrls = audioUrls
            blockEntity.urlWeights = urlWeights
            blockEntity.randomMode = randomMode
            blockEntity.currentUrlIndex = newIndex
            blockEntity.sendData()
            blockEntity.setChanged()
        }
    }
}
