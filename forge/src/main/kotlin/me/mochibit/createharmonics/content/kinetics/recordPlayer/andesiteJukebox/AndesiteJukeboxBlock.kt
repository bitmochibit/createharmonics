package me.mochibit.createharmonics.content.kinetics.recordPlayer.andesiteJukebox

import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlock
import me.mochibit.createharmonics.foundation.registry.ModBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

class AndesiteJukeboxBlock(
    properties: Properties,
) : RecordPlayerBlock(properties) {
    override fun getBlockEntityType(): BlockEntityType<out AndesiteJukeboxBlockEntity> = ModBlockEntities.ANDESITE_JUKEBOX.get()
}
