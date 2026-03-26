package me.mochibit.createharmonics.content.kinetics.recordPlayer.andesiteJukebox

import me.mochibit.createharmonics.content.kinetics.recordPlayer.AllDirectionRecordPlayerBlock
import me.mochibit.createharmonics.foundation.registry.ModBlockEntities
import net.minecraft.world.level.block.entity.BlockEntityType

class AndesiteJukeboxBlock(
    properties: Properties,
) : AllDirectionRecordPlayerBlock(properties) {
    override fun getBlockEntityType(): BlockEntityType<out AndesiteJukeboxBlockEntity> = ModBlockEntities.ANDESITE_JUKEBOX.get()
}
