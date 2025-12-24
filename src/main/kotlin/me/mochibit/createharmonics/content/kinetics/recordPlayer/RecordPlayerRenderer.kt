package me.mochibit.createharmonics.content.kinetics.recordPlayer

import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.AllPartialModels
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer.kineticRotationTransform
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer.standardKineticRotationTransform
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer
import dev.engine_room.flywheel.api.visualization.VisualizationManager
import me.mochibit.createharmonics.content.records.EtherealRecordItem
import me.mochibit.createharmonics.extension.lerpTo
import me.mochibit.createharmonics.registry.ModPartialModels
import net.createmod.catnip.animation.AnimationTickHolder
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.world.level.block.state.properties.BlockStateProperties

class RecordPlayerRenderer(
    val context: BlockEntityRendererProvider.Context,
) : SafeBlockEntityRenderer<RecordPlayerBlockEntity>() {
    override fun renderSafe(
        be: RecordPlayerBlockEntity,
        partialTicks: Float,
        ms: PoseStack,
        bufferSource: MultiBufferSource,
        light: Int,
        overlay: Int,
    ) {
        val currentLevel = be.level ?: return
        if (VisualizationManager.supportsVisualization(be.level)) return

        val blockState = be.blockState
        val discFacing = blockState.getValue(BlockStateProperties.FACING)
        val vb = bufferSource.getBuffer(RenderType.cutoutMipped())

        val shaftHalf =
            CachedBuffers.partialFacing(
                AllPartialModels.SHAFT_HALF,
                be.blockState,
                discFacing.opposite,
            )

        standardKineticRotationTransform(shaftHalf, be, light).renderInto(ms, vb)

        if (be.playerBehaviour.hasRecord()) {
            val recordItem = be.playerBehaviour.getRecord().item as? EtherealRecordItem
            val recordModel =
                recordItem?.let {
                    ModPartialModels.getRecordModel(it.recordType)
                } ?: return

            val discBuffer: SuperByteBuffer = CachedBuffers.partialFacing(recordModel, blockState, discFacing)

            val angle = be.getRotationAngle(partialTicks)

            kineticRotationTransform(discBuffer, be, discFacing.axis, angle, light).renderInto(ms, vb)
        }
    }
}
