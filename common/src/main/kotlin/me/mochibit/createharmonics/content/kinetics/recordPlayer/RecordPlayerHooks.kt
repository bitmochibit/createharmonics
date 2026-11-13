package me.mochibit.createharmonics.content.kinetics.recordPlayer

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.phys.AABB

/**
 * Record player hooks
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new RecordPlayerHooks
 */
object RecordPlayerHooks {
    /**
     * Returns `true` if playing record player
     *
     * @param state
     * @return `true` if the condition holds, `false` otherwise
     */
    @JvmStatic
    fun isPlayingRecordPlayer(state: BlockState): Boolean =
        state.block is RecordPlayerTrait &&
                state.getValue(RecordPlayerTrait.HAS_ETHEREAL_RECORD)


    /**
     * Sends the start
     *
     * @param level
     * @param pos
     * @param state
     */
    fun notifyStart(level: ServerLevel, pos: BlockPos, state: BlockState) =
        level.gameEvent(GameEvent.JUKEBOX_PLAY, pos, GameEvent.Context.of(state))

    /**
     * Sends the stop
     *
     * @param level
     * @param pos
     * @param state
     */
    fun notifyStop(level: ServerLevel, pos: BlockPos, state: BlockState) =
        level.gameEvent(GameEvent.JUKEBOX_STOP_PLAY, pos, GameEvent.Context.of(state))

    /**
     * Sends the client
     *
     * @param level
     * @param pos
     * @param playing
     */
    fun notifyClient(level: Level, pos: BlockPos, playing: Boolean) {
        level.getEntitiesOfClass(LivingEntity::class.java, AABB(pos).inflate(3.0))
            .forEach { it.setRecordPlayingNearby(pos, playing) }
    }
}