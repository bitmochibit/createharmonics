package me.mochibit.createharmonics.content.kinetics.recordPlayer.brassJukebox

import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlock
import me.mochibit.createharmonics.registry.ModBlockEntities
import net.minecraft.world.level.block.entity.BlockEntityType

class BrassJukeboxBlock(
    properties: Properties,
) : RecordPlayerBlock(properties) {
    override fun getBlockEntityType(): BlockEntityType<out BrassJukeboxBlockEntity> = ModBlockEntities.BRASS_JUKEBOX.get()
}
