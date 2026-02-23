package me.mochibit.createharmonics.content.kinetics.recordPlayer.andesiteJukebox

import com.simibubi.create.foundation.block.IBE
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlock
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlockEntity
import me.mochibit.createharmonics.registry.ModBlockEntities
import net.minecraft.world.level.block.entity.BlockEntityType

class AndesiteJukeboxBlock(
    properties: Properties,
) : RecordPlayerBlock(properties) {
    override fun getBlockEntityType(): BlockEntityType<out AndesiteJukeboxBlockEntity> = ModBlockEntities.ANDESITE_JUKEBOX.get()
}
