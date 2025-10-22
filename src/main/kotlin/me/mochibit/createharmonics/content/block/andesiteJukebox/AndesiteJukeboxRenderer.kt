package me.mochibit.createharmonics.content.block.andesiteJukebox

import com.simibubi.create.AllPartialModels
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer
import net.createmod.catnip.render.CachedBuffers
import net.createmod.catnip.render.SuperByteBuffer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.world.level.block.state.BlockState

class AndesiteJukeboxRenderer(dispatcher: BlockEntityRendererProvider.Context): KineticBlockEntityRenderer<AndesiteJukeboxBlockEntity>(dispatcher) {
    override fun getRotatedModel(
        be: AndesiteJukeboxBlockEntity,
        state: BlockState
    ): SuperByteBuffer? {
        return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state)
    }


}