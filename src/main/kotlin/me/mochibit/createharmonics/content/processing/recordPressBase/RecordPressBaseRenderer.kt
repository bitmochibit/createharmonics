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
         * Easing function for incoming items: starts fast and gradually decelerates to a stop.
         * Uses cubic easing for smooth deceleration.
         *
         * @param t Progress value between 0.0 and 1.0
         * @return Eased value between 0.0 and 1.0
         */
        private fun easeOutCubic(t: Float): Float {
            val f = t - 1.0f
            return f * f * f + 1.0f
        }

        /**
         * Easing function for outgoing items: starts from stop and gradually accelerates.
         * Uses cubic easing for smooth acceleration.
         *
         * @param t Progress value between 0.0 and 1.0
         * @return Eased value between 0.0 and 1.0
         */
        private fun easeInCubic(t: Float): Float = t * t * t

        /**
         * Calculates the eased animation progress for an item based on its belt position.
         *
         * @param prevPosition Previous frame's belt position
         * @param currentPosition Current frame's belt position
         * @param partialTicks Frame interpolation value
         * @param startPos Animation start position (1.0 for incoming, 0.5 for outgoing)
         * @param endPos Animation end position (0.5 for incoming, 0.0 for outgoing)
         * @param isIncoming true for incoming items (deceleration), false for outgoing (acceleration)
         * @return Eased position value
         */
        private fun calculateEasedPosition(
            prevPosition: Float,
            currentPosition: Float,
            partialTicks: Float,
            startPos: Float,
            endPos: Float,
            isIncoming: Boolean,
        ): Float {
            val rawProgress = Mth.lerp(partialTicks, prevPosition, currentPosition)

            // Normalize to 0-1 range
            val normalizedProgress = (rawProgress - startPos) / (endPos - startPos)
            val clampedProgress = normalizedProgress.coerceIn(0.0f, 1.0f)

            // Apply appropriate easing based on direction
            val easedProgress =
                if (isIncoming) {
                    easeOutCubic(clampedProgress) // Decelerate to stop
                } else {
                    easeInCubic(clampedProgress) // Accelerate from stop
                }

            // Convert back to position range
            return startPos + easedProgress * (endPos - startPos)
        }

        /**
         * Renders a single transported item with position offset and animation.
         */
        private fun renderTransportedItem(
            be: SmartBlockEntity,
            ms: PoseStack,
            buffer: MultiBufferSource,
            light: Int,
            overlay: Int,
            tis: TransportedItemStack,
            easedOffset: Float,
            partialTicks: Float,
            nudgeIndex: Int,
        ) {
            val msr = TransformStack.of(ms)
            val itemPosition = VecHelper.getCenterOf(be.getBlockPos())

            ms.pushPose()
            msr.nudge(nudgeIndex)

            var sideOffset = Mth.lerp(partialTicks, tis.prevSideOffset, tis.sideOffset)

            // Apply directional offset if item came from a horizontal direction
            if (tis.insertedFrom.axis.isHorizontal) {
                val offsetVec =
                    Vec3
                        .atLowerCornerOf(tis.insertedFrom.opposite.normal)
                        .scale((easedOffset - 0.5f).toDouble())
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

            // Render incoming items (animating from edge to center)
            for (tis in pressBehaviour.incoming) {
                val easedOffset =
                    calculateEasedPosition(
                        tis.prevBeltPosition,
                        tis.beltPosition,
                        partialTicks,
                        startPos = 1.0f,
                        endPos = 0.5f,
                        isIncoming = true, // Use deceleration easing
                    )

                renderTransportedItem(be, ms, buffer, light, overlay, tis, easedOffset, partialTicks, 0)
            }

            // Remove held item from incoming after rendering
            if (heldItem != null) {
                pressBehaviour.incoming.remove(heldItem)
            }

            pressBehaviour.outgoing.forEachIndexed { index, tis ->
                ms.pushPose()
                val msr = TransformStack.of(ms)
                val itemPosition = VecHelper.getCenterOf(be.getBlockPos())

                msr.nudge(index + 1) // Unique nudge for each item

                // Calculate eased animation offset for smooth outgoing movement
                val easedOffset =
                    calculateEasedPosition(
                        tis.prevBeltPosition,
                        tis.beltPosition,
                        partialTicks,
                        startPos = 0.5f,
                        endPos = 0.0f,
                        isIncoming = false, // Use acceleration easing
                    )

                // Position items in a circle around the center (matching Depot behavior)
                val angleStep = 360f / 8f // Divide circle into 8 positions
                val itemAngle = angleStep * index

                msr.rotateYDegrees(itemAngle)

                // Apply radial offset based on animation progress
                // As items move toward the edge, they move outward from center
                val radialOffset = 0.35 * (1.0 - (easedOffset - 0.0) / (0.5 - 0.0))
                ms.translate(radialOffset, 0.0, 0.0)

                // Stack items slightly higher to prevent z-fighting
                val yOffset = index * 0.001 // Tiny vertical offset per item
                ms.translate(0.0, yOffset, 0.0)

                // Check if item should render upright
                val renderUpright =
                    com.simibubi.create.content.kinetics.belt.BeltHelper
                        .isItemUpright(tis.stack)
                if (renderUpright) {
                    msr.rotateYDegrees(-itemAngle) // Counter-rotate upright items to face camera
                }

                val random = Random((index + 1).toLong())
                val randomAngle = (360 * random.nextFloat()).toInt()

                DepotRenderer.renderItem(
                    be.getLevel(),
                    ms,
                    buffer,
                    light,
                    overlay,
                    tis.stack,
                    if (renderUpright) randomAngle + 90 else randomAngle,
                    random,
                    itemPosition,
                    false,
                )
                ms.popPose()
            }

            ms.popPose()
        }
    }
}
