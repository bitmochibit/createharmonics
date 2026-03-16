package me.mochibit.createharmonics.foundation.behaviour.movement

import com.simibubi.create.api.behaviour.movement.MovementBehaviour
import com.simibubi.create.content.contraptions.AbstractContraptionEntity
import com.simibubi.create.content.contraptions.behaviour.MovementContext
import me.mochibit.createharmonics.foundation.network.packet.ContraptionBlockDataChangedPacket
import me.mochibit.createharmonics.foundation.registry.ForgeModPackets
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo
import net.minecraftforge.network.PacketDistributor
import kotlin.collections.set

abstract class SmartMovementBehaviour<LocalData, SyncedData> : MovementBehaviour {
    data class ContextData<out L, out S>(
        val local: L,
        val synced: S,
    )

    /**
     * Factory method for context data
     */
    abstract fun createContextData(context: MovementContext): ContextData<LocalData, SyncedData>

    abstract fun CompoundTag.writeData(
        data: SyncedData,
        context: MovementContext,
    )

    abstract fun CompoundTag.readData(target: SyncedData)

    override fun writeExtraData(context: MovementContext) {
        context.data.writeData(context.contextData.synced, context)
    }

    val MovementContext.contextData: ContextData<LocalData, SyncedData>
        @Suppress("UNCHECKED_CAST")
        get() {
            val temp = temporaryData
            if (temp is ContextData<*, *>) return temp as ContextData<LocalData, SyncedData>
            return createContextData(this).also { temporaryData = it }
        }

    fun syncFromBlock(context: MovementContext) {
        val nbt = context.contraption.blocks[context.localPos]?.nbt ?: return
        nbt.readData(context.contextData.synced)
    }

    fun resyncData(context: MovementContext) {
        val block = context.contraption.blocks[context.localPos] ?: return
        val nbt = block.nbt ?: return
        nbt.writeData(context.contextData.synced, context)
        context.contraption.entity.setBlockData(context.localPos, block)
    }
}

/**
 * Create mod lacks a way to update contraption block data without replacing the entire block state
 * This function updates the block data of a contraption block and syncs it to clients
 */
fun AbstractContraptionEntity.handleBlockDataChange(
    localPos: BlockPos,
    newData: CompoundTag,
) {
    if (contraption == null || !contraption.blocks.containsKey(localPos)) return
    val info: StructureBlockInfo = contraption.blocks[localPos] ?: return
    contraption.blocks[localPos] = StructureBlockInfo(info.pos(), info.state, newData)
}

fun AbstractContraptionEntity.setBlockData(
    localPos: BlockPos,
    newInfo: StructureBlockInfo,
) {
    contraption.blocks[localPos] = newInfo
    ForgeModPackets.channel.send(
        PacketDistributor.TRACKING_ENTITY.with { this },
        ContraptionBlockDataChangedPacket(id, localPos, newInfo.nbt ?: CompoundTag()),
    )
}
