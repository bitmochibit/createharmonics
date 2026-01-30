package me.mochibit.createharmonics.content.kinetics.recordPlayer

import com.simibubi.create.AllPartialModels
import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual
import dev.engine_room.flywheel.api.instance.Instance
import dev.engine_room.flywheel.api.visual.DynamicVisual
import dev.engine_room.flywheel.api.visual.TickableVisual
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.lib.instance.InstanceTypes
import dev.engine_room.flywheel.lib.instance.TransformedInstance
import dev.engine_room.flywheel.lib.model.Models
import dev.engine_room.flywheel.lib.model.baked.PartialModel
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual
import dev.engine_room.flywheel.lib.visual.SimpleTickableVisual
import me.mochibit.createharmonics.content.records.EtherealRecordItem
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.extension.lerpTo
import me.mochibit.createharmonics.registry.ModPartialModels
import net.createmod.catnip.math.AngleHelper
import net.minecraft.core.Direction
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import java.util.function.Consumer

class RecordPlayerVisual(
    context: VisualizationContext,
    blockEntity: RecordPlayerBlockEntity,
    partialTick: Float,
) : OrientedRotatingVisual<RecordPlayerBlockEntity>(
        context,
        blockEntity,
        partialTick,
        Direction.SOUTH,
        blockEntity.blockState.getValue(BlockStateProperties.FACING).opposite,
        Models.partial(AllPartialModels.SHAFT_HALF),
    ),
    SimpleTickableVisual,
    SimpleDynamicVisual {
    private val discFacing = blockState.getValue(BlockStateProperties.FACING)

    private var rotation = 0.0
    private var previousRotation = 0.0

    private var currentSpeed = 0.0f
    private val speedSmoothingFactor = 0.1f

    private var targetSpeed = 0.0f
    private val decelerationFactor = 0.05f // Slower deceleration for gradual stop

    private var currentModel: PartialModel =
        ModPartialModels.getRecordModel(blockEntity.playerBehaviour.getRecordItem()?.recordType ?: RecordType.BRASS)

    private val disc: TransformedInstance =
        instancerProvider()
            .instancer(
                InstanceTypes.TRANSFORMED,
                Models.partial(currentModel),
            ).createInstance()
            .apply {
                setVisible(false)
                setChanged()
            }

    override fun _delete() {
        super._delete()
        disc.delete()
    }

    private fun getRotation(partialTick: Double): Float = AngleHelper.angleLerp(partialTick, previousRotation, rotation)

    override fun collectCrumblingInstances(consumer: Consumer<Instance?>) {
        super.collectCrumblingInstances(consumer)
        consumer.accept(disc)
    }

    override fun updateLight(p0: Float) {
        super.updateLight(p0)
        val inFront = pos.relative(discFacing)
        val behind = pos.relative(discFacing.opposite)
        relight(inFront, disc)
        relight(behind, rotatingModel)
    }

    override fun tick(context: TickableVisual.Context) {
        if (blockEntity.playerBehaviour.hasRecord()) {
            val recordType = (blockEntity.playerBehaviour.getRecord().item as EtherealRecordItem).recordType
            val newModel = ModPartialModels.getRecordModel(recordType)
            if (newModel != currentModel) {
                currentModel = newModel
                instancerProvider()
                    .instancer(InstanceTypes.TRANSFORMED, Models.partial(currentModel))
                    .stealInstance(disc)
            }
            disc.setVisible(true)
        } else {
            disc.setVisible(false)
        }

        previousRotation = rotation

        // Determine target speed based on playback state
        val playbackState = blockEntity.playerBehaviour.playbackState
        targetSpeed =
            when (playbackState) {
                RecordPlayerBehaviour.PlaybackState.PLAYING -> blockEntity.speed

                RecordPlayerBehaviour.PlaybackState.PAUSED,
                RecordPlayerBehaviour.PlaybackState.MANUALLY_PAUSED,
                RecordPlayerBehaviour.PlaybackState.STOPPED,
                -> 0.0f
            }

        // Smoothly interpolate to target speed
        // Use slower deceleration when stopping, faster acceleration when starting
        val smoothingFactor = if (targetSpeed < currentSpeed) decelerationFactor else speedSmoothingFactor
        currentSpeed = currentSpeed.lerpTo(targetSpeed, smoothingFactor)

        val deg: Float = currentSpeed * 5

        rotation += (deg / 20).toDouble()

        rotation %= 360.0
    }

    override fun beginFrame(ctx: DynamicVisual.Context?) {
        disc
            .setIdentityTransform()
            .translate(visualPosition)
            .translate(
                discFacing.normal.x * .95f,
                discFacing.normal.y * .95f,
                discFacing.normal.z * .95f,
            ).center()
            .rotateToFace(discFacing)
            .rotateZDegrees(getRotation(ctx?.partialTick()?.toDouble() ?: 0.0))
            .uncenter()
            .setChanged()
    }
}
