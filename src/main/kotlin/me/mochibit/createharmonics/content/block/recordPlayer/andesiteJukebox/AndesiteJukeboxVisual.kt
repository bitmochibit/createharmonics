package me.mochibit.createharmonics.content.block.recordPlayer.andesiteJukebox

import com.simibubi.create.AllPartialModels
import com.simibubi.create.content.kinetics.base.RotatingInstance
import com.simibubi.create.foundation.render.AllInstanceTypes
import dev.engine_room.flywheel.api.instance.Instance
import dev.engine_room.flywheel.api.visual.DynamicVisual
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.lib.model.Models
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual
import me.mochibit.createharmonics.registry.ModPartialModels
import net.minecraft.core.Direction
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import java.util.function.Consumer

class AndesiteJukeboxVisual(
    context: VisualizationContext,
    blockEntity: AndesiteJukeboxBlockEntity,
    partialTick: Float
) : com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual<AndesiteJukeboxBlockEntity>(
    context,
    blockEntity,
    partialTick
), SimpleDynamicVisual {

    private val shaftFacing = blockState.getValue(BlockStateProperties.FACING)
    private val discFacing = shaftFacing.opposite

    private val disc: RotatingInstance =
        instancerProvider().instancer(
            AllInstanceTypes.ROTATING,
            Models.partial(ModPartialModels.ETHEREAL_DISC)
        ).createInstance().apply {
            setPosition(visualPosition)
                .nudge(
                    discFacing.normal.x * .9f,
                    discFacing.normal.y * .9f,
                    discFacing.normal.z * .9f
                )
            setRotationAxis(shaftFacing.axis)
            rotateToFace(discFacing)
            setVisible(false)
            setChanged()
        }

    private val shaft: RotatingInstance = instancerProvider().instancer(
        AllInstanceTypes.ROTATING,
        Models.partial(AllPartialModels.SHAFT_HALF)
    ).createInstance().apply {
        setPosition(visualPosition)
        rotateToFace(Direction.SOUTH, shaftFacing)
        setup(blockEntity)
        setChanged()
    }

    override fun update(partialTick: Float) {
        shaft.setup(blockEntity).setChanged()
    }

    override fun beginFrame(context: DynamicVisual.Context) {
        animateDisc(context.partialTick())
    }

    val discSpeed: Float
        get() {
            return blockEntity.speed / 4
        }

    private fun animateDisc(partialTick: Float) {
        if (!blockEntity.hasDisc()) {
            disc.setVisible(false)
        } else {
            disc.setVisible(true)
        }
        disc.setup(blockEntity, discSpeed).setChanged()
    }

    override fun _delete() {
        disc.delete()
        shaft.delete()
    }

    override fun collectCrumblingInstances(consumer: Consumer<Instance?>) {
        consumer.accept(disc)
        consumer.accept(shaft)
    }

    override fun updateLight(p0: Float) {
        val behind = pos.relative(shaftFacing)
        relight(behind, shaft)

        val inFront = pos.relative(discFacing)
        relight(inFront, disc)
    }
}