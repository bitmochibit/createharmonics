package me.mochibit.createharmonics.content.block.recordBurner

import com.simibubi.create.AllPartialModels
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.lib.model.Models

class RecordBurnerVisual(
    context: VisualizationContext,
    blockEntity: RecordPressBaseBlockEntity,
    partialTick: Float,
) : SingleAxisRotatingVisual<RecordPressBaseBlockEntity>(
        context,
        blockEntity,
        partialTick,
        Models.partial(AllPartialModels.SHAFT_HALF),
    )
