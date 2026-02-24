package me.mochibit.createharmonics.content.kinetics.recordPlayer.brassJukebox

import me.mochibit.createharmonics.content.kinetics.recordPlayer.AllDirectionRecordPlayerBlock
import me.mochibit.createharmonics.registry.ModBlockEntities
import net.minecraft.world.level.block.entity.BlockEntityType

class BrassJukeboxBlock(
    properties: Properties,
) : AllDirectionRecordPlayerBlock(properties) {
    override fun getBlockEntityType(): BlockEntityType<out BrassJukeboxBlockEntity> = ModBlockEntities.BRASS_JUKEBOX.get()
}
