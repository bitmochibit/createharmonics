package me.mochibit.createharmonics.content.uploader

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer
import me.mochibit.createharmonics.foundation.registry.ModPartialModels
import net.createmod.catnip.animation.AnimationTickHolder
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BeaconRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.Direction
import net.minecraft.util.FastColor
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.sin

class AmethystCatalystRenderer(context: Context) :
    SmartBlockEntityRenderer<AmethystCatalystBlockEntity>(context) {

    companion object {
        private const val SPIN_DEGREES_PER_TICK = 3.0
        private const val BOB_AMPLITUDE = 1.0 / 16.0
        private const val BOB_PERIOD_TICKS = 40.0
        private const val OFFSET_Y = 0.8f

        private const val CRYSTAL_TIP_OFFSET = 0.35f

        private val UPLOAD_COLOR = 0xFFA855F7.toInt()
        private val DELETE_COLOR = 0xFFEF4444.toInt()
    }

    private fun bobOffset(time: Float): Float =
        (sin(time * 2.0 * PI / BOB_PERIOD_TICKS) * BOB_AMPLITUDE).toFloat()

    override fun renderSafe(
        be: AmethystCatalystBlockEntity,
        partialTicks: Float,
        ms: PoseStack,
        buffer: MultiBufferSource,
        light: Int,
        overlay: Int,
    ) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay)
        renderBeam(be, partialTicks, ms, buffer)
        if (!be.hasCrystal) return

        val level = be.level ?: return
        val time = AnimationTickHolder.getRenderTime(level)

        val angle = (time * SPIN_DEGREES_PER_TICK) % 360.0

        CachedBuffers.partial(ModPartialModels.amethystModel, be.blockState)
            .rotateCentered(Mth.DEG_TO_RAD * angle.toFloat(), Direction.UP)
            .translate(0.0, (bobOffset(time) + OFFSET_Y).toDouble(), 0.0)
            .light<SuperByteBuffer>(light)
            .overlay<SuperByteBuffer>(overlay)
            .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()))
    }

    private fun renderBeam(
        be: AmethystCatalystBlockEntity,
        partialTicks: Float,
        ms: PoseStack,
        buffer: MultiBufferSource,
    ) {
        val level = be.level ?: return
        val frame = be.beamFrame(partialTicks) ?: return
        val time = AnimationTickHolder.getRenderTime(level)
        val tipY = OFFSET_Y  + CRYSTAL_TIP_OFFSET

        CatalystBeam.render(
            ms, buffer, frame, level.gameTime, partialTicks,
            color = if (frame.descending) DELETE_COLOR else UPLOAD_COLOR,
            tipY = tipY,
        )
    }

    override fun getRenderBoundingBox(be: AmethystCatalystBlockEntity): AABB {
        val p = be.blockPos
        return AABB(
            p.x.toDouble(), p.y.toDouble(), p.z.toDouble(),
            p.x + 1.0, BeaconRenderer.MAX_RENDER_Y.toDouble(), p.z + 1.0,
        )
    }

    override fun shouldRenderOffScreen(be: AmethystCatalystBlockEntity) = true
    override fun getViewDistance() = 256

    override fun shouldRender(be: AmethystCatalystBlockEntity, cameraPos: Vec3): Boolean =
        Vec3.atCenterOf(be.blockPos).multiply(1.0, 0.0, 1.0)
            .closerThan(cameraPos.multiply(1.0, 0.0, 1.0), viewDistance.toDouble())
}


object CatalystBeam {
    private const val INNER_RADIUS = 0.05f
    private const val GLOW_RADIUS = 0.07f
    private const val GLOW_ALPHA = 32
    private const val CORNER_RADIUS = 0.04f
    private const val CORNER_Y = 0.5f

    private const val CORNER_POSITION = 0.25f


    private val CORNERS = arrayOf(
        Vector3f(-CORNER_POSITION, CORNER_Y, -CORNER_POSITION),
        Vector3f(CORNER_POSITION, CORNER_Y, -CORNER_POSITION),
        Vector3f(-CORNER_POSITION, CORNER_Y, CORNER_POSITION),
        Vector3f(CORNER_POSITION, CORNER_Y, CORNER_POSITION),
    )

    fun render(
        ms: PoseStack,
        buffer: MultiBufferSource,
        frame: BeamFrame,
        gameTime: Long,
        partialTicks: Float,
        color: Int,
        tipY: Float,
    ) {
        val t = Math.floorMod(gameTime, 40) + partialTicks

        ms.pushPose()
        ms.translate(0.5, 0.0, 0.5)

        if (frame.head > 0f) renderMainBeam(ms, buffer, frame, color, tipY, t)

        ms.popPose()
    }


    private fun renderMainBeam(
        ms: PoseStack,
        buffer: MultiBufferSource,
        frame: BeamFrame,
        color: Int,
        tipY: Float,
        t: Float,
    ) {
        val top = tipY + frame.length
        val front = frame.length * frame.head
        val back = frame.length * frame.tail


        val yMin: Float
        val yMax: Float
        if (frame.descending) {
            yMax = top - back
            yMin = top - front
        } else {
            yMin = tipY + back
            yMax = tipY + front
        }
        if (yMax - yMin < 0.01f) return

        val signed = if (frame.descending) t else -t
        val scroll = -1f + Mth.frac(signed * 0.2f - Mth.floor(signed * 0.1f).toFloat())
        val innerV = 0.5f / INNER_RADIUS
        val glowV = 1f
        val r = INNER_RADIUS
        val g = GLOW_RADIUS

        // Inner rotating beam
        ms.pushPose()
        ms.mulPose(Axis.YP.rotationDegrees(t * 2.25f - 45f))
        prism(
            ms.last(), buffer.getBuffer(RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, false)),
            color, yMin, yMax,
            scroll + (yMin - tipY) * innerV, scroll + (yMax - tipY) * innerV,
            0f, r, r, 0f, -r, 0f, 0f, -r,
        )
        ms.popPose()

        // Outer glow
        prism(
            ms.last(), buffer.getBuffer(RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, true)),
            FastColor.ARGB32.color(GLOW_ALPHA, color), yMin, yMax,
            scroll + (yMin - tipY) * glowV, scroll + (yMax - tipY) * glowV,
            -g, -g, g, -g, -g, g, g, g,
        )
    }

    private fun prism(
        pose: PoseStack.Pose, vc: VertexConsumer, color: Int,
        y0: Float, y1: Float, v0: Float, v1: Float,
        x1: Float, z1: Float, x2: Float, z2: Float,
        x3: Float, z3: Float, x4: Float, z4: Float,
    ) {
        quad(pose, vc, color, y0, y1, v0, v1, x1, z1, x2, z2)
        quad(pose, vc, color, y0, y1, v0, v1, x4, z4, x3, z3)
        quad(pose, vc, color, y0, y1, v0, v1, x2, z2, x4, z4)
        quad(pose, vc, color, y0, y1, v0, v1, x3, z3, x1, z1)
    }

    private fun quad(
        pose: PoseStack.Pose, vc: VertexConsumer, color: Int,
        y0: Float, y1: Float, v0: Float, v1: Float,
        xa: Float, za: Float, xb: Float, zb: Float,
    ) {
        vertex(pose, vc, color, y1, xa, za, 1f, v1)
        vertex(pose, vc, color, y0, xa, za, 1f, v0)
        vertex(pose, vc, color, y0, xb, zb, 0f, v0)
        vertex(pose, vc, color, y1, xb, zb, 0f, v1)
    }

    private fun vertex(
        pose: PoseStack.Pose, vc: VertexConsumer, color: Int,
        y: Float, x: Float, z: Float, u: Float, v: Float,
    ) {
        vc.addVertex(pose, x, y, z)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal(pose, 0f, 1f, 0f)
    }
}