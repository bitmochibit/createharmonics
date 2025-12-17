package me.mochibit.createharmonics.content.block.recordPressBase

import dev.engine_room.flywheel.api.instance.Instance
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual
import java.util.function.Consumer

class RecordPressBaseVisual(
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
