package me.mochibit.createharmonics.content.block.recordPlayer

import com.simibubi.create.AllPartialModels
import com.simibubi.create.content.contraptions.behaviour.MovementContext
import com.simibubi.create.content.contraptions.render.ActorVisual
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual
import com.simibubi.create.content.kinetics.base.RotatingInstance
import com.simibubi.create.foundation.render.AllInstanceTypes
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.lib.instance.InstanceTypes
import dev.engine_room.flywheel.lib.instance.TransformedInstance
import dev.engine_room.flywheel.lib.model.Models
import me.mochibit.createharmonics.content.item.EtherealRecordItem
import me.mochibit.createharmonics.extension.lerpTo
import me.mochibit.createharmonics.registry.ModPartialModels
import net.createmod.catnip.animation.AnimationTickHolder
import net.createmod.catnip.math.AngleHelper
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraftforge.items.ItemStackHandler

class RecordPlayerActorVisual(
    context: VisualizationContext,
    val world: VirtualRenderWorld,
    val movementContext: MovementContext
) : ActorVisual(
    context,
    world,
    movementContext
) {

    private val discFacing = movementContext.state.getValue(BlockStateProperties.FACING)
    private val axis: Direction.Axis = KineticBlockEntityVisual.rotationAxis(movementContext.state)
    private val blockState: BlockState = movementContext.state

    private var rotation: Double = 0.0
    private var previousRotation: Double = 0.0

    private var currentSpeed = 0.0f
    private val speedSmoothingFactor = 0.1f

    val inventoryHandler = object : ItemStackHandler(1) {
        override fun isItemValid(slot: Int, stack: ItemStack): Boolean {
            return stack.item is EtherealRecordItem
        }
    }.apply {
        movementContext.blockEntityData.contains("inventory").let { hasInventory ->
            if (hasInventory) {
                val nbt = movementContext.blockEntityData.getCompound("inventory")
                this.deserializeNBT(nbt)
            }
        }
    }

    val disc: TransformedInstance =
        instancerProvider.instancer(InstanceTypes.TRANSFORMED, Models.partial(ModPartialModels.ETHEREAL_RECORD)).createInstance()
            .apply {
                light(localBlockLight(), 0)
                setChanged()
            }

    val shaft: RotatingInstance =
        instancerProvider.instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT_HALF)).createInstance()
            .apply {
                setRotationAxis(axis)
                setRotationOffset(KineticBlockEntityVisual.rotationOffset(blockState, axis, movementContext.localPos))
                setPosition(movementContext.localPos)
                rotateToFace(Direction.SOUTH, blockState.getValue(BlockStateProperties.FACING).opposite)
                light(localBlockLight(), 0)
                setChanged()
            }

    private fun hasRecord(): Boolean {
        val record = inventoryHandler.getStackInSlot(0)
        return !record.isEmpty && record.item is EtherealRecordItem
    }

    override fun tick() {
        if (hasRecord()) {
            disc.setVisible(true)
        } else {
            disc.setVisible(false)
        }

        if (context.disabled) return

        previousRotation = rotation

        currentSpeed = currentSpeed.lerpTo(context.animationSpeed/20, speedSmoothingFactor)

        val deg: Float = currentSpeed * 5

        rotation += (deg / 20).toDouble()

        rotation %= 360.0
    }

    override fun beginFrame() {
        disc
            .setIdentityTransform()
            .translate(context.localPos)
            .translate(
                discFacing.normal.x * .9f,
                discFacing.normal.y * .9f,
                discFacing.normal.z * .9f
            )
            .center()
            .rotateToFace(discFacing)
            .rotateZDegrees(getRotation())
            .uncenter()
            .setChanged()
    }

    private fun getRotation(): Float {
        return AngleHelper.angleLerp(AnimationTickHolder.getPartialTicks().toDouble(), previousRotation, rotation)
    }

    override fun _delete() {
        disc.delete()
        shaft.delete()
    }
}