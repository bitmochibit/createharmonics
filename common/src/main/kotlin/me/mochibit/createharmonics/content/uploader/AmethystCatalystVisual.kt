package me.mochibit.createharmonics.content.uploader

import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual
import dev.engine_room.flywheel.api.instance.Instance
import dev.engine_room.flywheel.api.visual.DynamicVisual
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.lib.instance.InstanceTypes
import dev.engine_room.flywheel.lib.instance.TransformedInstance
import dev.engine_room.flywheel.lib.model.Models
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual
import me.mochibit.createharmonics.foundation.registry.ModPartialModels
import java.util.function.Consumer
import kotlin.math.PI
import kotlin.math.sin

class AmethystCatalystVisual(
    ctx: VisualizationContext,
    blockEntity: AmethystCatalystBlockEntity,
    partialTick: Float,
) : AbstractBlockEntityVisual<AmethystCatalystBlockEntity>(ctx, blockEntity, partialTick),
    SimpleDynamicVisual {

    private companion object {
        const val SPIN_DEGREES_PER_TICK = 3.0
        const val BOB_AMPLITUDE = 1.0 / 16.0
        const val BOB_PERIOD_TICKS = 40.0
    }

    private val behaviour: AmethystCatalystBehaviour? by lazy {
        blockEntity.getBehaviour(AmethystCatalystBehaviour.BEHAVIOUR_TYPE)
    }

    private var crystalShown = false

    private val crystal: TransformedInstance =
        instancerProvider()
            .instancer(InstanceTypes.TRANSFORMED, Models.partial(ModPartialModels.amethystModel))
            .createInstance()
            .apply {
                setVisible(false)
                setChanged()
            }

    init {
        relight(crystal)
    }

    override fun beginFrame(ctx: DynamicVisual.Context) {
        val hasCrystal = behaviour?.hasCrystal == true

        if (hasCrystal != crystalShown) {
            crystalShown = hasCrystal
            crystal.setVisible(hasCrystal)
            crystal.setChanged()
        }
        if (!hasCrystal || doDistanceLimitThisFrame(ctx)) return


        val offsetY = .8f
        val time = level.gameTime + ctx.partialTick().toDouble()
        val angle = (time * SPIN_DEGREES_PER_TICK) % 360.0
        val bob = sin(time * 2.0 * PI / BOB_PERIOD_TICKS) * BOB_AMPLITUDE

        crystal
            .setIdentityTransform()
            .translate(visualPosition)
            .translate(0.0, bob + offsetY, 0.0)
            .center()
            .rotateYDegrees(angle.toFloat())
            .uncenter()
            .setChanged()
    }

    override fun updateLight(partialTick: Float) {
        relight(crystal)
    }

    override fun collectCrumblingInstances(consumer: Consumer<Instance?>) {
        consumer.accept(crystal)
    }

    override fun _delete() {
        crystal.delete()
    }
}