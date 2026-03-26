package me.mochibit.createharmonics.foundation.behaviour.movement

import com.simibubi.create.api.behaviour.movement.MovementBehaviour
import com.simibubi.create.content.contraptions.AbstractContraptionEntity
import com.simibubi.create.content.contraptions.behaviour.MovementContext
import me.mochibit.createharmonics.foundation.eventbus.EventBus
import me.mochibit.createharmonics.foundation.eventbus.ProxyEvent
import me.mochibit.createharmonics.foundation.network.packet.ContraptionBlockDataChangedPacket
import me.mochibit.createharmonics.foundation.network.packet.ModPacket
import me.mochibit.createharmonics.foundation.registry.ModPackets
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo
import net.minecraftforge.network.PacketDistributor
import kotlin.collections.set

abstract class SmartMovementBehaviour<Data> : MovementBehaviour {
    abstract fun contextDataFactory(context: MovementContext): Data

    enum class SyncType {
        NET,
        DISK,
    }

    init {
        EventBus.on<ProxyEvent.PlayerStartTrackingEntityProxy> { event ->
            val entity = event.entity
            if (entity !is AbstractContraptionEntity) return@on

            entity.contraption.actors.forEach { (_, context) ->
                if (context.contraption.entity.stringUUID == event.entity.stringUUID) {
                    context.resync()
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun getContextData(context: MovementContext): Data {
        if (context.temporaryData == null) {
            context.temporaryData =
                contextDataFactory(context).apply {
                    read(context, context.data, this, SyncType.DISK)
                }
            if (!context.world.isClientSide) {
                resyncData(context)
            }
        }
        return context.temporaryData as Data
    }

    /**
     * Serialize data in compound tags for disk and networking
     */
    abstract fun write(
        target: CompoundTag,
        contextData: Data,
        context: MovementContext,
        syncType: SyncType,
    )

    /**
     * Deserialize data and updates [target]
     */
    abstract fun read(
        context: MovementContext,
        from: CompoundTag,
        target: Data,
        syncType: SyncType,
    )

    override fun writeExtraData(context: MovementContext) {
        write(context.data, getContextData(context), context, SyncType.DISK)
    }

    fun syncFromBlock(context: MovementContext) {
        val nbt = context.contraption.blocks[context.localPos]?.nbt ?: return
        read(context, nbt, getContextData(context), SyncType.NET)
    }

    /**
     * Equivalent to [com.simibubi.create.foundation.blockEntity.SmartBlockEntity.setChanged] but for contraptions
     */
    fun resyncData(context: MovementContext) {
        val block = context.contraption.blocks[context.localPos] ?: return
        val nbt = block.nbt ?: return
        write(nbt, getContextData(context), context, SyncType.NET)
        context.contraption.entity.setBlockData(context.localPos, block)
    }
}

/**
 * Create mod lacks a way to update contraption block data without replacing the entire block state
 * This function updates the block data of a contraption block and syncs it to clients
 */

inline fun <reified DataType> MovementContext.getContextData(): DataType? = this.temporaryData as? DataType

fun MovementContext.resync() {
    val actor = this.contraption.getActorAt(localPos) ?: return
    val state = actor.left.state
    val behaviour = MovementBehaviour.REGISTRY.get(state)
    if (behaviour is SmartMovementBehaviour<*>) {
        behaviour.resyncData(this)
    }
}

fun AbstractContraptionEntity.handleBlockDataChange(
    localPos: BlockPos,
    newData: CompoundTag,
) {
    if (contraption == null || !contraption.blocks.containsKey(localPos)) return
    val info: StructureBlockInfo = contraption.blocks[localPos] ?: return
    val context = contraption.getActorAt(localPos)?.right ?: return
    contraption.blocks[localPos] = StructureBlockInfo(info.pos(), info.state, newData)
    val behaviour = MovementBehaviour.REGISTRY.get(info.state)
    if (behaviour is SmartMovementBehaviour<*>) {
        behaviour.syncFromBlock(context)
    }
}

fun AbstractContraptionEntity.setBlockData(
    localPos: BlockPos,
    newInfo: StructureBlockInfo,
) {
    contraption.blocks[localPos] = newInfo
    ModPackets.sendToTrackingEntity(ContraptionBlockDataChangedPacket(id, localPos, newInfo.nbt ?: CompoundTag()), this)
}
