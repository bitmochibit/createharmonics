package me.mochibit.createharmonics.content.kinetics.recordPlayer

import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour
import com.simibubi.create.foundation.gui.AllIcons
import dev.engine_room.flywheel.api.visualization.VisualizationManager
import me.mochibit.createharmonics.extension.lerpTo
import net.createmod.catnip.math.AngleHelper
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
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

    enum class PlaybackMode : INamedIconOptions {
        PLAY(AllIcons.I_PLAY),
        PAUSE(AllIcons.I_PAUSE),
        ;

        private val translationKey: String
        private val icon: AllIcons

        constructor(icon: AllIcons) {
            this.icon = icon
            this.translationKey = "createharmonics.record_player.playback_mode." + name.lowercase()
        }

        override fun getIcon(): AllIcons = icon

        override fun getTranslationKey(): String = translationKey
    }

    lateinit var playerBehaviour: RecordPlayerBehaviour
        private set

    lateinit var playbackMode: ScrollOptionBehaviour<PlaybackMode>
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

        playbackMode =
            ScrollOptionBehaviour(
                PlaybackMode::class.java,
                Component.translatable("createharmonics.record_player.playback_mode"),
                this,
                RecordPlayerValueBoxTransform { blockState, direction ->
                    val axis: Direction.Axis = direction.axis
                    val beAxis: Direction.Axis =
                        blockState
                            .getValue(BlockStateProperties.FACING)
                            .axis
                    beAxis !== axis
                },
            )
        behaviours.add(playbackMode)
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
