package me.mochibit.createharmonics.content.kinetics.recordPlayer

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.phys.AABB

object RecordPlayerHooks {
    @JvmStatic
    fun isPlayingRecordPlayer(state: BlockState): Boolean =
        state.block is RecordPlayerTrait &&
                state.getValue(RecordPlayerTrait.HAS_ETHEREAL_RECORD)


    fun notifyStart(level: ServerLevel, pos: BlockPos, state: BlockState) =
        level.gameEvent(GameEvent.JUKEBOX_PLAY, pos, GameEvent.Context.of(state))

    fun notifyStop(level: ServerLevel, pos: BlockPos, state: BlockState) =
        level.gameEvent(GameEvent.JUKEBOX_STOP_PLAY, pos, GameEvent.Context.of(state))

    fun notifyClient(level: Level, pos: BlockPos, playing: Boolean) {
        level.getEntitiesOfClass(LivingEntity::class.java, AABB(pos).inflate(3.0))
            .forEach { it.setRecordPlayingNearby(pos, playing) }
    }
}