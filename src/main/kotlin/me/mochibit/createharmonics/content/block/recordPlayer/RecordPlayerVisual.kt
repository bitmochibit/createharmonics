package me.mochibit.createharmonics.content.block.recordPlayer

import com.simibubi.create.AllPartialModels
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual
import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual
import com.simibubi.create.content.kinetics.base.RotatingInstance
import com.simibubi.create.content.kinetics.base.ShaftVisual
import com.simibubi.create.foundation.render.AllInstanceTypes
import dev.engine_room.flywheel.api.instance.Instance
import dev.engine_room.flywheel.api.visual.DynamicVisual
import dev.engine_room.flywheel.api.visual.TickableVisual
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.lib.instance.InstanceTypes
import dev.engine_room.flywheel.lib.instance.TransformedInstance
import dev.engine_room.flywheel.lib.model.Models
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual
import dev.engine_room.flywheel.lib.visual.SimpleTickableVisual
import me.mochibit.createharmonics.extension.lerpTo
import me.mochibit.createharmonics.registry.ModPartialModels
import net.createmod.catnip.math.AngleHelper
import net.minecraft.core.Direction
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import java.util.function.Consumer

class RecordPlayerVisual(
    context: VisualizationContext,
    blockEntity: RecordPlayerBlockEntity,
    partialTick: Float
) : OrientedRotatingVisual<RecordPlayerBlockEntity>(
    context,
    blockEntity,
    partialTick,
    Direction.SOUTH,
    blockEntity.blockState.getValue(BlockStateProperties.FACING).opposite,
    Models.partial(AllPartialModels.SHAFT_HALF)
), SimpleTickableVisual, SimpleDynamicVisual {

    private val discFacing = blockState.getValue(BlockStateProperties.FACING)

    private var rotation = 0.0
    private var previousRotation = 0.0

    private var currentSpeed = 0.0f
    private val speedSmoothingFactor = 0.1f

    private val disc: TransformedInstance =
        instancerProvider().instancer(
            InstanceTypes.TRANSFORMED,
            Models.partial(ModPartialModels.ETHEREAL_RECORD)
        ).createInstance()

    override fun _delete() {
        super._delete()
        disc.delete()
    }

    private fun getRotation(partialTick: Double): Float {
        return AngleHelper.angleLerp(partialTick, previousRotation, rotation)
    }

    override fun collectCrumblingInstances(consumer: Consumer<Instance?>) {
        super.collectCrumblingInstances(consumer)
        consumer.accept(disc)
    }

    override fun updateLight(p0: Float) {
        super.updateLight(p0)
        relight(disc)
    }


    override fun tick(context: TickableVisual.Context) {
        if (!blockEntity.hasRecord()) {
            disc.setVisible(false)
        } else {
            disc.setVisible(true)
        }

        previousRotation = rotation

        currentSpeed = currentSpeed.lerpTo(blockEntity.speed, speedSmoothingFactor)

        val deg: Float = currentSpeed * 5

        rotation += (deg / 20).toDouble()

        rotation %= 360.0
    }

    override fun beginFrame(ctx: DynamicVisual.Context?) {
        disc
            .setIdentityTransform()
            .translate(visualPosition)
            .translate(
                discFacing.normal.x * .9f,
                discFacing.normal.y * .9f,
                discFacing.normal.z * .9f
            )
            .center()
            .rotateToFace(discFacing)
            .rotateZDegrees(getRotation(ctx?.partialTick()?.toDouble() ?: 0.0))
            .uncenter()
            .setChanged()
    }


}