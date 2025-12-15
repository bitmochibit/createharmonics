package me.mochibit.createharmonics.content.block.recordBurner

import com.simibubi.create.AllPartialModels
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual
import dev.engine_room.flywheel.api.instance.Instance
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.lib.model.Models
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual
import java.util.function.Consumer

class RecordBurnerVisual(
    context: VisualizationContext,
    blockEntity: RecordPressBaseBlockEntity,
    partialTick: Float,
) : AbstractBlockEntityVisual<RecordPressBaseBlockEntity>(
        context,
        blockEntity,
        partialTick,
    ) {
    override fun _delete() {
    }

    override fun collectCrumblingInstances(consumer: Consumer<Instance?>?) {
    }

    override fun updateLight(partialTick: Float) {
    }
}
