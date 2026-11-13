package me.mochibit.createharmonics.foundation.behaviour.movement

import com.simibubi.create.api.behaviour.movement.MovementBehaviour
import com.simibubi.create.content.contraptions.AbstractContraptionEntity
import com.simibubi.create.content.contraptions.behaviour.MovementContext
import me.mochibit.createharmonics.foundation.network.packet.ContraptionBlockDataChangedPacket
import me.mochibit.createharmonics.foundation.registry.ModPackets
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo
import kotlin.collections.set

/**
 * Stainable
 *
 * @property isDirty the is dirty
 */
interface Stainable {
    var isDirty: Boolean

    /**
     * Mark dirty
     *
     */
    fun markDirty() {
        isDirty = true
    }

    /**
     * Clean
     *
     */
    fun clean() {
        isDirty = false
    }
}

/**
 * Stops the aware data
 *
 * @property stopMovingCalled the stop moving called
 */
interface StopAwareData {
    var stopMovingCalled: Boolean
}

/**
 * Smart movement behaviour
 *
 * @param Data
 * @constructor Creates a new SmartMovementBehaviour
 */
abstract class SmartMovementBehaviour<Data : Stainable> : MovementBehaviour {
    /**
     * Context data factory
     *
     * @param context
     * @return the data
     */
    abstract fun contextDataFactory(context: MovementContext): Data

    /**
     * Resets the type
     * Enum — a fixed set of named constants.
     *
     * @constructor Creates a new SyncType
     */
    enum class SyncType {
        /**
         * Net
         *
         * @constructor Creates a new NET
         */
        NET,

        /**
         * Disk
         *
         * @constructor Creates a new DISK
         */
        DISK,
    }

    /**
     * On stop tracking
     *
     * @param blockEntityData the block entity data
     */
    abstract fun onStopTracking(blockEntityData: CompoundTag)

    /**
     * Returns the context data
     *
     * @param context
     * @return the data
     */
    @Suppress("UNCHECKED_CAST")
    fun getContextData(context: MovementContext): Data {
        if (context.temporaryData == null) {
            context.temporaryData =
                contextDataFactory(context).apply {
                    read(context, context.data, this, SyncType.DISK, context.world.registryAccess())
                }
            if (!context.world.isClientSide) {
                resyncData(context)
            }
        }
        return context.temporaryData as Data
    }

    /**
     * Serialize data in compound tags for disk and networking
     *
     * @param target
     * @param contextData the context data
     * @param context
     * @param syncType the sync type
     * @param provider
     */
    abstract fun write(
        target: CompoundTag,
        contextData: Data,
        context: MovementContext,
        syncType: SyncType,
        provider: HolderLookup.Provider,
    )

    /**
     * Deserialize data and updates [target]
     *
     * @param context
     * @param from
     * @param target
     * @param syncType the sync type
     * @param provider
     */
    abstract fun read(
        context: MovementContext,
        from: CompoundTag,
        target: Data,
        syncType: SyncType,
        provider: HolderLookup.Provider,
    )

    override fun writeExtraData(context: MovementContext) {
        write(context.data, getContextData(context), context, SyncType.DISK, context.world.registryAccess())
    }

    /**
     * Resets the from block
     *
     * @param context
     */
    fun syncFromBlock(context: MovementContext) {
        val nbt = context.contraption.blocks[context.localPos]?.nbt ?: return
        val data = getContextData(context)
        data.markDirty()
        read(context, nbt, data, SyncType.NET, context.world.registryAccess())
    }

    /**
     * Equivalent to [com.simibubi.create.foundation.blockEntity.SmartBlockEntity.setChanged] but for contraptions
     *
     * @param context
     * @param checkForDirty the check for dirty
     */
    fun resyncData(
        context: MovementContext,
        checkForDirty: Boolean = false,
    ) {
        val data = getContextData(context)
        if (checkForDirty && !data.isDirty) {
            return
        }
        val block = context.contraption.blocks[context.localPos] ?: return
        val nbt = block.nbt ?: return
        write(nbt, data, context, SyncType.NET, context.world.registryAccess())
        context.contraption.entity.setBlockData(context.localPos, block)
        data.clean()
    }

    /**
     * Sends the stopped tracking
     *
     * @param context
     */
    fun notifyStoppedTracking(context: MovementContext) {
        val data = context.temporaryData ?: return
        if (data is StopAwareData && data.stopMovingCalled) return
        context.blockEntityData?.let(::onStopTracking)
    }
}

/**
 * Create mod lacks a way to update contraption block data without replacing the entire block state
 * This function updates the block data of a contraption block and syncs it to clients
 *
 * @param DataType
 * @return the data type
 */

inline fun <reified DataType> MovementContext.getContextData(): DataType? = this.temporaryData as? DataType

/**
 * Handles the block data change on the receiver [AbstractContraptionEntity]
 *
 * @param localPos the local pos
 * @param newData the new data
 */
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

/**
 * Sets the block data on the receiver [AbstractContraptionEntity]
 *
 * @param localPos the local pos
 * @param newInfo the new info
 */
fun AbstractContraptionEntity.setBlockData(
    localPos: BlockPos,
    newInfo: StructureBlockInfo,
) {
    contraption.blocks[localPos] = newInfo
    ModPackets.sendToTrackingEntity(ContraptionBlockDataChangedPacket(id, localPos, newInfo.nbt ?: CompoundTag()), this)
}


