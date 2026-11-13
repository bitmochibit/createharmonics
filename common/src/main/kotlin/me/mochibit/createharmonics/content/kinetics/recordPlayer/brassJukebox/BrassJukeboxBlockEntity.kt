package me.mochibit.createharmonics.content.kinetics.recordPlayer.brassJukebox

import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

/**
 * Brass jukebox block entity
 *
 * @constructor
 * @param type
 * @param pos
 * @param state
 */
class BrassJukeboxBlockEntity(
    type: BlockEntityType<BrassJukeboxBlockEntity>,
    pos: BlockPos,
    state: BlockState,
) : RecordPlayerBlockEntity(type, pos, state)
