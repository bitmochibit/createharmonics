package me.mochibit.createharmonics.registry

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.simibubi.create.foundation.gui.AllIcons
import me.mochibit.createharmonics.asResource
import net.createmod.catnip.theme.Color
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import org.joml.Matrix4f

object ModIcons {
    private val ATLAS = "textures/gui/icons.png".asResource()
    private const val ATLAS_SIZE = 256
    private const val ICON_SIZE = 16

    private class ModIcon(
        x: Int,
        y: Int,
        private val atlas: ResourceLocation,
    ) : AllIcons(x, y) {
        private val iconX = x * ICON_SIZE
        private val iconY = y * ICON_SIZE

        @OnlyIn(Dist.CLIENT)
        override fun render(
            graphics: GuiGraphics,
            x: Int,
            y: Int,
        ) {
            graphics.blit(
                atlas,
                x,
                y,
                0,
                iconX.toFloat(),
                iconY.toFloat(),
                ICON_SIZE,
                ICON_SIZE,
                ATLAS_SIZE,
                ATLAS_SIZE,
            )
        }

        @OnlyIn(Dist.CLIENT)
        override fun render(
            ms: PoseStack,
            buffer: MultiBufferSource,
            color: Int,
        ) {
            val builder = buffer.getBuffer(RenderType.text(ATLAS))
            val matrix = ms.last().pose()
            val rgb = Color(color)
            val light = LightTexture.FULL_BRIGHT

            val vec1 = Vec3(0.0, 0.0, 0.0)
            val vec2 = Vec3(0.0, 1.0, 0.0)
            val vec3 = Vec3(1.0, 1.0, 0.0)
            val vec4 = Vec3(1.0, 0.0, 0.0)

            val u1 = iconX * 1f / ATLAS_SIZE
            val u2 = (iconX + ICON_SIZE) * 1f / ATLAS_SIZE
            val v1 = iconY * 1f / ATLAS_SIZE
            val v2 = (iconY + ICON_SIZE) * 1f / ATLAS_SIZE

            vertex(builder, matrix, vec1, rgb, u1, v1, light)
            vertex(builder, matrix, vec2, rgb, u1, v2, light)
            vertex(builder, matrix, vec3, rgb, u2, v2, light)
            vertex(builder, matrix, vec4, rgb, u2, v1, light)
        }

        @OnlyIn(Dist.CLIENT)
        private fun vertex(
            builder: VertexConsumer,
            matrix: Matrix4f,
            vec: Vec3,
            rgb: Color,
            u: Float,
            v: Float,
            light: Int,
        ) {
            builder
                .vertex(matrix, vec.x.toFloat(), vec.y.toFloat(), vec.z.toFloat())
                .color(rgb.red, rgb.green, rgb.blue, 255)
                .uv(u, v)
                .uv2(light)
                .endVertex()
        }

        override fun bind() {
            RenderSystem.setShaderTexture(0, atlas)
        }
    }

    @JvmField
    val I_PLAY_PITCH_STATIC: AllIcons = ModIcon(0, 0, ATLAS)

    @JvmField
    val I_PAUSE_PITCH_STATIC: AllIcons = ModIcon(1, 0, ATLAS)
}
