package me.mochibit.createharmonics.content.processing.recordPressBase

import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack
import com.simibubi.create.content.logistics.depot.DepotRenderer
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer
import dev.engine_room.flywheel.lib.transform.TransformStack
import net.createmod.catnip.math.VecHelper
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import java.util.Random

class RecordPressBaseRenderer(
    context: BlockEntityRendererProvider.Context,
) : SafeBlockEntityRenderer<RecordPressBaseBlockEntity>() {
    override fun renderSafe(
        be: RecordPressBaseBlockEntity,
        partialTicks: Float,
        ms: PoseStack,
        buffer: MultiBufferSource,
        light: Int,
        overlay: Int,
    ) {
        renderItemsOf(be, partialTicks, ms, buffer, light, overlay, be.behaviour)
    }

    companion object {
        /**
         * Easing function for deceleration: starts at full speed and smoothly slows to a stop.
         * Uses quadratic easing out (1 - (1-t)^2) for natural friction-like deceleration.
         */
        private fun easeOutQuad(t: Float): Float = 1f - (1f - t) * (1f - t)

        /**
         * Easing function for acceleration: starts slow and gradually speeds up to belt speed.
         * Uses quadratic easing in (t^2) for smooth acceleration.
         */
        private fun easeInQuad(t: Float): Float = t * t

        /**
         * Renders a single transported item with position offset and animation.
         * Incoming items use deceleration easing, outgoing items use linear movement.
         */
        private fun renderTransportedItem(
            be: SmartBlockEntity,
            ms: PoseStack,
            buffer: MultiBufferSource,
            light: Int,
            overlay: Int,
            tis: TransportedItemStack,
            partialTicks: Float,
            nudgeIndex: Int,
            isIncoming: Boolean,
        ) {
            val msr = TransformStack.of(ms)
            val itemPosition = VecHelper.getCenterOf(be.getBlockPos())

            ms.pushPose()
            msr.nudge(nudgeIndex)

            // Linear interpolation of raw position
            val rawOffset = Mth.lerp(partialTicks, tis.prevBeltPosition, tis.beltPosition).coerceAtLeast(0f)

            // Apply easing based on direction
            val offset =
                if (isIncoming && rawOffset > 0.5f) {
                    // Incoming: decelerate from belt speed to stop
                    // Map from [1.0, 0.5] to [0, 1] for easing input
                    val progress = (1.0f - rawOffset) / 0.5f
                    val easedProgress = easeOutQuad(progress)
                    // Map back to [1.0, 0.5] range
                    1.0f - easedProgress * 0.5f
                } else if (!isIncoming && rawOffset < 0.5f && rawOffset >= 0.0f) {
                    // Outgoing: accelerate from stop to belt speed
                    // Map from [0.5, 0.0] to [0, 1] for easing input
                    val progress = (0.5f - rawOffset) / 0.5f
                    val easedProgress = easeInQuad(progress)
                    // Map back to [0.5, 0.0] range
                    0.5f - easedProgress * 0.5f
                } else {
                    rawOffset
                }

            var sideOffset = Mth.lerp(partialTicks, tis.prevSideOffset, tis.sideOffset)

            // Apply directional offset if item came from a horizontal direction
            if (tis.insertedFrom.axis.isHorizontal) {
                // When offset = 1.0 (at edge): 1.0 - 0.5 = 0.5 (half block away)
                // When offset = 0.5 (at center): 0.5 - 0.5 = 0.0 (no offset)
                val offsetVec =
                    Vec3
                        .atLowerCornerOf(tis.insertedFrom.opposite.normal)
                        .scale((offset - 0.5f).toDouble())
                ms.translate(offsetVec.x, offsetVec.y, offsetVec.z)

                val alongX = tis.insertedFrom.clockWise.axis === Direction.Axis.X
                if (!alongX) sideOffset *= -1f
                ms.translate(
                    if (alongX) sideOffset.toDouble() else 0.0,
                    0.0,
                    if (alongX) 0.0 else sideOffset.toDouble(),
                )
            }

            DepotRenderer.renderItem(
                be.getLevel(),
                ms,
                buffer,
                light,
                overlay,
                tis.stack,
                tis.angle,
                Random(0),
                itemPosition,
                false,
            )
            ms.popPose()
        }

        /**
         * Main rendering function for all items on the record press base.
         * Handles incoming items (sliding in), held items (stationary), and outgoing items (sliding out).
         * Uses linear interpolation like the Depot for consistent movement.
         */
        fun renderItemsOf(
            be: SmartBlockEntity,
            partialTicks: Float,
            ms: PoseStack,
            buffer: MultiBufferSource,
            light: Int,
            overlay: Int,
            pressBehaviour: RecordPressBaseBehaviour,
        ) {
            val heldItem = pressBehaviour.heldItem

            ms.pushPose()
            ms.translate(.5f, 15 / 16f, .5f)

            // Temporarily add held item to incoming list for unified rendering
            if (heldItem != null) {
                pressBehaviour.incoming.add(heldItem)
            }

            // Render incoming items (animating from edge to center with deceleration)
            for (tis in pressBehaviour.incoming) {
                renderTransportedItem(be, ms, buffer, light, overlay, tis, partialTicks, 0, isIncoming = true)
            }

            // Remove held item from incoming after rendering
            if (heldItem != null) {
                pressBehaviour.incoming.remove(heldItem)
            }

            // Render outgoing items (animating from center to edge, linear)
            pressBehaviour.outgoing.forEachIndexed { index, tis ->
                renderTransportedItem(be, ms, buffer, light, overlay, tis, partialTicks, index + 1, isIncoming = false)
            }

            ms.popPose()
        }
    }
}
