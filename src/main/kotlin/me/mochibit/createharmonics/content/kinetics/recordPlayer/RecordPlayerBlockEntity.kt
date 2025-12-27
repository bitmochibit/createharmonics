package me.mochibit.createharmonics.content.kinetics.recordPlayer

import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import dev.engine_room.flywheel.api.visualization.VisualizationManager
import me.mochibit.createharmonics.extension.lerpTo
import net.createmod.catnip.math.AngleHelper
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.items.ItemStackHandler

abstract class RecordPlayerBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState,
) : KineticBlockEntity(type, pos, state) {
    companion object {
        fun handlePlaybackEnd(playerId: String) {
            val blockEntity = RecordPlayerBehaviour.getBlockEntityByPlayerUUID(playerId)
            blockEntity?.playerBehaviour?.onPlaybackEnd(playerId)
        }

        fun handleAudioTitleChange(
            playerId: String,
            newTitle: String,
        ) {
            val blockEntity = RecordPlayerBehaviour.getBlockEntityByPlayerUUID(playerId)
            blockEntity?.playerBehaviour?.onAudioTitleUpdate(newTitle)
        }
    }

    lateinit var playerBehaviour: RecordPlayerBehaviour
        private set

    var visualSpeed = 0f
    private val visualSpeedSmoothFactor = 0.1f

    private var accumulatedRotation = 0.0
    private var previousRotation = 0.0

    override fun tick() {
        super.tick()

        if (level?.isClientSide == true && !VisualizationManager.supportsVisualization(level)) {
            visualSpeed = visualSpeed.lerpTo(this.speed, visualSpeedSmoothFactor)
        }
    }

    fun getRotationAngle(partialTicks: Float): Float {
        previousRotation = accumulatedRotation
        val deg = visualSpeed / (360 * 5)
        accumulatedRotation += deg
        accumulatedRotation %= 360.0

        return AngleHelper.angleLerp(partialTicks.toDouble(), previousRotation, accumulatedRotation)
    }

    override fun addBehaviours(behaviours: MutableList<BlockEntityBehaviour>) {
        playerBehaviour = RecordPlayerBehaviour(this)
        behaviours.add(playerBehaviour)
    }

    override fun <T> getCapability(
        cap: Capability<T?>,
        side: Direction?,
    ): LazyOptional<T?> {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return playerBehaviour.lazyItemHandler.cast()
        }
        return super.getCapability(cap, side)
    }

    fun applyInventoryToBlock(wrapped: ItemStackHandler) {
        for (i in 0 until itemHandler.slots) {
            itemHandler.setStackInSlot(i, if (i < wrapped.slots) wrapped.getStackInSlot(i) else ItemStack.EMPTY)
        }
    }

    val lazyItemHandler: LazyOptional<RecordPlayerItemHandler>
        get() = playerBehaviour.lazyItemHandler

    val itemHandler: RecordPlayerItemHandler
        get() = playerBehaviour.itemHandler
}
