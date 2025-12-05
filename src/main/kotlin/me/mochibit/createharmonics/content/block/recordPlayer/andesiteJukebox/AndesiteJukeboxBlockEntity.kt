package me.mochibit.createharmonics.content.block.recordPlayer.andesiteJukebox

import me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

class AndesiteJukeboxBlockEntity(
    type: BlockEntityType<AndesiteJukeboxBlockEntity>,
    pos: BlockPos,
    state: BlockState,
) : RecordPlayerBlockEntity(type, pos, state)
