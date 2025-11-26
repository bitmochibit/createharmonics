package me.mochibit.createharmonics.content.block.recordPlayer.andesiteJukebox

import com.simibubi.create.foundation.block.IBE
import me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerBlock
import me.mochibit.createharmonics.registry.ModBlockEntitiesRegistry
import net.minecraft.world.level.block.entity.BlockEntityType

class AndesiteJukeboxBlock(properties: Properties) : RecordPlayerBlock(properties), IBE<AndesiteJukeboxBlockEntity> {
    override fun getBlockEntityClass(): Class<AndesiteJukeboxBlockEntity> {
        return AndesiteJukeboxBlockEntity::class.java
    }

    override fun getBlockEntityType(): BlockEntityType<out AndesiteJukeboxBlockEntity> {
        return ModBlockEntitiesRegistry.ANDESITE_JUKEBOX.get()
    }
}