package me.mochibit.createharmonics.content.kinetics.recordPlayer

import com.simibubi.create.content.kinetics.base.KineticBlock
import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour
import dev.engine_room.flywheel.api.visualization.VisualizationManager
import me.mochibit.createharmonics.config.ModStressConfig
import me.mochibit.createharmonics.foundation.extension.lerpTo
import me.mochibit.createharmonics.foundation.inventory.StorageInventory
import net.createmod.catnip.math.AngleHelper
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.world.Clearable
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.items.ItemStackHandler
import kotlin.math.abs


abstract class RecordPlayerBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState,
) : KineticBlockEntity(type, pos, state),
    Clearable {
    private companion object { const val DEGREES_PER_TICK_PER_RPM = 1f / 600f }

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
            previousRotation = accumulatedRotation
            accumulatedRotation = (accumulatedRotation + visualSpeed * DEGREES_PER_TICK_PER_RPM) % 360.0
        }
        refreshNetworkStress()
    }

    fun getRotationAngle(partialTicks: Float): Float =
        AngleHelper.angleLerp(partialTicks.toDouble(), previousRotation, accumulatedRotation)

    override fun remove() {
        super.remove()
    }

    override fun addBehaviours(behaviours: MutableList<BlockEntityBehaviour>) {
        playerBehaviour = RecordPlayerBehaviour(this)
        behaviours.add(playerBehaviour)
        behaviours.add(DirectBeltInputBehaviour(this).allowingBeltFunnels())

        playbackMode =
            ScrollOptionBehaviour(
                PlaybackMode::class.java,
                Component.translatable("createharmonics.record_player.playback_mode"),
                this,
                RecordPlayerValueBoxTransform { blockState, direction ->
                    val axis: Direction.Axis = direction.axis
                    val beAxis: Direction.Axis = (blockState.block as? KineticBlock)?.getRotationAxis(blockState) ?: direction.axis
                    beAxis !== axis
                },
            )
        behaviours.add(playbackMode)
    }

    override fun clearContent() {
        for (i in 0 until itemHandler.getSlots()) {
            itemHandler.setStackInSlot(i, ItemStack.EMPTY)
        }
    }

    fun applyInventoryToBlock(wrapped: ItemStackHandler) {
        for (i in 0 until itemHandler.getSlots()) {
            itemHandler.setStackInSlot(i, if (i < wrapped.slots) wrapped.getStackInSlot(i) else ItemStack.EMPTY)
        }
    }

    private var cachedStressImpact = Float.NaN

    fun refreshNetworkStress() {
        if (level?.isClientSide == true || !hasNetwork()) return
        val newStress = calculateStressApplied()
        if (cachedStressImpact == newStress) return
        cachedStressImpact = newStress
        orCreateNetwork.updateStressFor(this, newStress)
        setChanged()
    }

    override fun onSpeedChanged(previousSpeed: Float) {
        super.onSpeedChanged(previousSpeed)

        cachedStressImpact = Float.NaN
    }

    override fun calculateStressApplied(): Float {
        val base = ModStressConfig.getImpact(stressConfigKey)?.asDouble?.toFloat() ?: 0f
        val applied = RecordPlayerStress.impact(
            base, playerBehaviour.playbackState, playerBehaviour.speedInterrupted,
            playerBehaviour.isStaticPitchMode, abs(theoreticalSpeed),
        )
        lastStressApplied = applied
        return applied
    }

    val itemHandler: StorageInventory
        get() = playerBehaviour.itemHandler
}
