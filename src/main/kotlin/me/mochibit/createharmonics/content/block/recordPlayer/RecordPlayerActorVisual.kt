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
import dev.engine_room.flywheel.lib.model.baked.PartialModel
import me.mochibit.createharmonics.content.item.EtherealRecordItem
import me.mochibit.createharmonics.content.item.record.RecordType
import me.mochibit.createharmonics.extension.lerpTo
import me.mochibit.createharmonics.registry.ModPartialModels
import net.createmod.catnip.animation.AnimationTickHolder
import net.createmod.catnip.math.AngleHelper
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties

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

    private var currentModel: PartialModel = ModPartialModels.getRecordModel(RecordType.BRASS)


    val disc: TransformedInstance =
        instancerProvider.instancer(InstanceTypes.TRANSFORMED, Models.partial(currentModel)).createInstance()
            .apply {
                light(localBlockLight(), 0)
                setVisible(false)
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


    private fun getInventoryHandler(): RecordPlayerMountedStorage? {
        val storageManager = context.contraption.storage
        val rpInventory = storageManager.allItemStorages.get(context.localPos) as? RecordPlayerMountedStorage
        return rpInventory
    }

    private fun hasRecord(): Boolean {
        val handler = getInventoryHandler() ?: return false
        val record: ItemStack = handler.getStackInSlot(0)
        return !record.isEmpty && record.item is EtherealRecordItem
    }

    private fun getRecord(): EtherealRecordItem? {
        val handler = getInventoryHandler() ?: return null
        val record: ItemStack = handler.getStackInSlot(0)
        if (record.isEmpty || record.item !is EtherealRecordItem) return null
        return record.item as EtherealRecordItem
    }

    override fun tick() {
        val recordItem = getRecord()
        if (recordItem != null) {
            if (currentModel != ModPartialModels.getRecordModel(recordItem.recordType)) {
                currentModel = ModPartialModels.getRecordModel(recordItem.recordType)
                instancerProvider.instancer(InstanceTypes.TRANSFORMED, Models.partial(currentModel)).stealInstance(disc)
            }
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
                discFacing.normal.x * .95f,
                discFacing.normal.y * .95f,
                discFacing.normal.z * .95f
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