package me.mochibit.createharmonics.content.processing.recordPressBase

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import com.simibubi.create.content.kinetics.belt.BeltHelper
import com.simibubi.create.content.logistics.box.PackageItem
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer
import dev.engine_room.flywheel.lib.transform.TransformStack
import net.createmod.catnip.math.VecHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.random.Random

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

        fun renderItemsOf(
            be: SmartBlockEntity,
            partialTicks: Float,
            ms: PoseStack,
            buffer: MultiBufferSource,
            light: Int,
            overlay: Int,
            depotBehaviour: RecordPressBaseBehaviour,
        ) {
            val transported = depotBehaviour.heldItem
            val msr = TransformStack.of(ms)
            val itemPosition = VecHelper.getCenterOf(be.getBlockPos())

            ms.pushPose()
            ms.translate(.5f, 15 / 16f, .5f)

            if (transported != null) depotBehaviour.incoming.add(transported)

            // Render main items
            for (tis in depotBehaviour.incoming) {
                ms.pushPose()
                msr.nudge(0)
                val offset = Mth.lerp(partialTicks, tis.prevBeltPosition, tis.beltPosition)
                var sideOffset = Mth.lerp(partialTicks, tis.prevSideOffset, tis.sideOffset)

                if (tis.insertedFrom
                        .getAxis()
                        .isHorizontal()
                ) {
                    val offsetVec =
                        Vec3
                            .atLowerCornerOf(
                                tis.insertedFrom
                                    .getOpposite()
                                    .getNormal(),
                            ).scale((.5f - offset).toDouble())
                    ms.translate(offsetVec.x, offsetVec.y, offsetVec.z)
                    val alongX =
                        tis.insertedFrom
                            .getClockWise()
                            .getAxis() === Direction.Axis.X
                    if (!alongX) sideOffset *= -1f
                    ms.translate(if (alongX) sideOffset else 0f, 0f, if (alongX) 0f else sideOffset)
                }

                val itemStack = tis.stack
                val angle = tis.angle
                val r = Random(0)
                renderItem(be.getLevel(), ms, buffer, light, overlay, itemStack, angle, r, itemPosition, false)
                ms.popPose()
            }

            if (transported != null) depotBehaviour.incoming.remove(transported)

            // Render output items
            for (i in 0..<depotBehaviour.processingOutputBuffer.getSlots()) {
                val stack = depotBehaviour.processingOutputBuffer.getStackInSlot(i)
                if (stack.isEmpty()) continue
                ms.pushPose()
                msr.nudge(i)

                val renderUpright = BeltHelper.isItemUpright(stack)
                msr.rotateYDegrees(360 / 8f * i)
                ms.translate(.35f, 0f, 0f)
                if (renderUpright) msr.rotateYDegrees(-(360 / 8f * i))
                val r = Random((i + 1).toLong())
                val angle = (360 * r.nextFloat()).toInt()
                renderItem(
                    be.getLevel(),
                    ms,
                    buffer,
                    light,
                    overlay,
                    stack,
                    if (renderUpright) angle + 90 else angle,
                    r,
                    itemPosition,
                    false,
                )
                ms.popPose()
            }

            ms.popPose()
        }

        fun renderItem(
            level: Level?,
            ms: PoseStack,
            buffer: MultiBufferSource,
            light: Int,
            overlay: Int,
            itemStack: ItemStack,
            angle: Int,
            r: Random?,
            itemPosition: Vec3,
            alwaysUpright: Boolean,
        ) {
            val itemRenderer =
                Minecraft
                    .getInstance()
                    .getItemRenderer()
            val msr = TransformStack.of(ms)
            val count = (Mth.log2((itemStack.getCount()))) / 2
            val bakedModel = itemRenderer.getModel(itemStack, null, null, 0)
            val blockItem = bakedModel.isGui3d()
            val renderUpright = (BeltHelper.isItemUpright(itemStack) || alwaysUpright) && !blockItem

            ms.pushPose()
            msr.rotateYDegrees(angle.toFloat())

            if (renderUpright) {
                val renderViewEntity = Minecraft.getInstance().cameraEntity
                if (renderViewEntity != null) {
                    val positionVec = renderViewEntity.position()
                    val vectorForOffset = itemPosition
                    val diff = vectorForOffset.subtract(positionVec)
                    val yRot = (Mth.atan2(diff.x, diff.z) + Math.PI).toFloat()
                    ms.mulPose(Axis.YP.rotation(yRot))
                }
                ms.translate(0.0, 3 / 32.0, (-1 / 16f).toDouble())
            }

            for (i in 0..count) {
                ms.pushPose()
                if (blockItem && r != null) ms.translate(r.nextFloat() * .0625f * i, 0f, r.nextFloat() * .0625f * i)

                if (PackageItem.isPackage(itemStack) && !alwaysUpright) {
                    ms.translate(0f, 4 / 16f, 0f)
                    ms.scale(1.5f, 1.5f, 1.5f)
                } else if (blockItem && alwaysUpright) {
                    ms.translate(0f, 1 / 16f, 0f)
                    ms.scale(.755f, .755f, .755f)
                } else {
                    ms.scale(.5f, .5f, .5f)
                }

                if (!blockItem && !renderUpright) {
                    ms.translate(0f, -3 / 16f, 0f)
                    msr.rotateXDegrees(90f)
                }
                itemRenderer.render(itemStack, ItemDisplayContext.FIXED, false, ms, buffer, light, overlay, bakedModel)
                ms.popPose()

                if (!renderUpright) {
                    if (!blockItem) msr.rotateYDegrees(10f)
                    ms.translate(0.0, if (blockItem) 1 / 64.0 else 1 / 16.0, 0.0)
                } else {
                    ms.translate(0f, 0f, -1 / 16f)
                }
            }

            ms.popPose()
        }
    }
}
