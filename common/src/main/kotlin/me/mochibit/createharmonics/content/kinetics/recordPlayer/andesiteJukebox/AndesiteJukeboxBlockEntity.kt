package me.mochibit.createharmonics.content.kinetics.recordPlayer.andesiteJukebox

import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

/**
 * Andesite jukebox block entity
 *
 * @constructor
 * @param type
 * @param pos
 * @param state
 */
class AndesiteJukeboxBlockEntity(
    type: BlockEntityType<AndesiteJukeboxBlockEntity>,
    pos: BlockPos,
    state: BlockState,
) : RecordPlayerBlockEntity(type, pos, state)
