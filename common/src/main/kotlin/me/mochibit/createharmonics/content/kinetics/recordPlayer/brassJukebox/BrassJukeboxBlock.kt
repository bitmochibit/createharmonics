package me.mochibit.createharmonics.content.kinetics.recordPlayer.brassJukebox

import me.mochibit.createharmonics.content.kinetics.recordPlayer.HorizontalRecordPlayerBlock
import me.mochibit.createharmonics.foundation.registry.ModBlockEntities
import net.minecraft.world.level.block.entity.BlockEntityType

class BrassJukeboxBlock(
    properties: Properties,
) : HorizontalRecordPlayerBlock(properties) {
    override fun getBlockEntityType(): BlockEntityType<out BrassJukeboxBlockEntity> = ModBlockEntities.BRASS_JUKEBOX.get()
}
