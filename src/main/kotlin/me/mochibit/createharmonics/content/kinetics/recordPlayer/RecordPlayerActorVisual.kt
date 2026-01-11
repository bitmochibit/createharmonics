package me.mochibit.createharmonics.content.kinetics.recordPlayer

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
import me.mochibit.createharmonics.content.records.EtherealRecordItem
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.extension.lerpTo
import me.mochibit.createharmonics.registry.ModPartialModels
import net.createmod.catnip.animation.AnimationTickHolder
import net.createmod.catnip.math.AngleHelper
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties

class RecordPlayerActorVisual(
    vCtx: VisualizationContext,
    vRw: VirtualRenderWorld,
    mCtx: MovementContext,
) : ActorVisual(
        vCtx,
        vRw,
        mCtx,
    ) {
    private val discFacing = context.state.getValue(BlockStateProperties.FACING)
    private val axis: Direction.Axis = KineticBlockEntityVisual.rotationAxis(context.state)
    private val blockState: BlockState = context.state

    private var rotation: Double = 0.0
    private var previousRotation: Double = 0.0

    private var currentSpeed = 0.0f
    private val speedSmoothingFactor = 0.1f

    private var currentModel: PartialModel = ModPartialModels.getRecordModel(RecordType.BRASS)

    private var cachedRecordType: RecordType? = null

    val disc: TransformedInstance =
        instancerProvider
            .instancer(InstanceTypes.TRANSFORMED, Models.partial(currentModel))
            .createInstance()
            .apply {
                light(localBlockLight(), 0)
                setVisible(false)
                setChanged()
            }

    val shaft: RotatingInstance =
        instancerProvider
            .instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT_HALF))
            .createInstance()
            .apply {
                setRotationAxis(axis)
                setRotationOffset(KineticBlockEntityVisual.rotationOffset(blockState, axis, context.localPos))
                setPosition(context.localPos)
                rotateToFace(Direction.SOUTH, blockState.getValue(BlockStateProperties.FACING).opposite)
                light(localBlockLight(), 0)
                setChanged()
            }

    private fun getRecord(): EtherealRecordItem? {
        val handler =
            context.contraption.storage.allItemStorages[context.localPos] as? RecordPlayerMountedStorage
                ?: return null
        val record: ItemStack = handler.getRecord()
        if (record.isEmpty || record.item !is EtherealRecordItem) return null
        return record.item as EtherealRecordItem
    }

    override fun tick() {
        val recordItem = getRecord()
        val newRecordType = recordItem?.recordType

        if (newRecordType != cachedRecordType) {
            cachedRecordType = newRecordType
            updateRecordModel(newRecordType)
        }

        if (context.disabled) return

        previousRotation = rotation

        currentSpeed = currentSpeed.lerpTo(context.animationSpeed / 20, speedSmoothingFactor)

        val deg: Float = currentSpeed * 5

        rotation += (deg / 20).toDouble()

        rotation %= 360.0
    }

    private fun updateRecordModel(recordType: RecordType?) {
        if (recordType != null) {
            currentModel = ModPartialModels.getRecordModel(recordType)
            instancerProvider.instancer(InstanceTypes.TRANSFORMED, Models.partial(currentModel)).stealInstance(disc)
            disc.setVisible(true)
        } else {
            disc.setVisible(false)
        }
    }

    override fun beginFrame() {
        disc
            .setIdentityTransform()
            .translate(context.localPos)
            .translate(
                discFacing.normal.x * .95f,
                discFacing.normal.y * .95f,
                discFacing.normal.z * .95f,
            ).center()
            .rotateToFace(discFacing)
            .rotateZDegrees(getRotation())
            .uncenter()
            .setChanged()
    }

    private fun getRotation(): Float = AngleHelper.angleLerp(AnimationTickHolder.getPartialTicks().toDouble(), previousRotation, rotation)

    override fun _delete() {
        disc.delete()
        shaft.delete()
    }
}
