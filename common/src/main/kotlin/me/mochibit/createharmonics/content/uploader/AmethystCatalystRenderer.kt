package me.mochibit.createharmonics.content.uploader

import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer
import com.simibubi.create.foundation.render.RenderTypes
import me.mochibit.createharmonics.foundation.registry.ModPartialModels
import net.createmod.catnip.animation.AnimationTickHolder
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

class AmethystCatalystRenderer(context: Context) :
    SmartBlockEntityRenderer<AmethystCatalystBlockEntity>(context) {

    companion object {
        private const val SPIN_DEGREES_PER_TICK = 3.0
        private const val BOB_AMPLITUDE = 1.0 / 16.0
        private const val BOB_PERIOD_TICKS = 40.0
        private const val GLOW_PERIOD_TICKS = 60.0
        private const val OFFSET_Y = 0.8f
    }

    override fun renderSafe(
        be: AmethystCatalystBlockEntity,
        partialTicks: Float,
        ms: PoseStack,
        buffer: MultiBufferSource,
        light: Int,
        overlay: Int,
    ) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay)

        val behaviour = be.getBehaviour(AmethystCatalystBehaviour.BEHAVIOUR_TYPE) ?: return
        if (!behaviour.hasCrystal) return

        val level = be.level ?: return

        val blockState = be.blockState
        val time = AnimationTickHolder.getRenderTime(level)

        val angle = (time * SPIN_DEGREES_PER_TICK) % 360.0
        val bob = sin(time * 2.0 * PI / BOB_PERIOD_TICKS) * BOB_AMPLITUDE

        CachedBuffers.partial(ModPartialModels.amethystModel, blockState)
            .rotateCentered(Mth.DEG_TO_RAD * angle.toFloat(), Direction.UP)
            .translate(0.0, bob + OFFSET_Y, 0.0)
            .light<SuperByteBuffer>(light)
            .overlay<SuperByteBuffer>(overlay)
            .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()))


    }
}