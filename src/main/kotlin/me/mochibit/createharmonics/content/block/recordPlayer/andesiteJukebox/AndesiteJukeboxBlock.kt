package me.mochibit.createharmonics.content.block.recordPlayer.andesiteJukebox

import com.simibubi.create.foundation.block.IBE
import me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerBlock
import me.mochibit.createharmonics.registry.ModBlockEntities
import net.minecraft.world.level.block.entity.BlockEntityType

class AndesiteJukeboxBlock(
    properties: Properties,
) : RecordPlayerBlock(properties),
    IBE<AndesiteJukeboxBlockEntity> {
    override fun getBlockEntityClass(): Class<AndesiteJukeboxBlockEntity> = AndesiteJukeboxBlockEntity::class.java

    override fun getBlockEntityType(): BlockEntityType<out AndesiteJukeboxBlockEntity> = ModBlockEntities.ANDESITE_JUKEBOX.get()
}
