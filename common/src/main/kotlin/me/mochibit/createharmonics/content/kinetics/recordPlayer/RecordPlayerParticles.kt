package me.mochibit.createharmonics.content.kinetics.recordPlayer

import me.mochibit.createharmonics.audio.player.PlayerState
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.particles.ShriekParticleOption
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.random.Random

/**
 * Record player particles
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new RecordPlayerParticles
 */
object RecordPlayerParticles {
    private fun shriek(level: Level, p: Vec3) =
        level.addParticle(ShriekParticleOption(2), false, p.x, p.y, p.z, 0.0, 12.5, 0.0)

    /**
     * Sends the static
     *
     * @param level
     * @param state
     * @param blockPos the block pos
     */
    fun emitStatic(level: Level, state: PlayerState, blockPos: BlockPos) {
        val pos = Vec3.atBottomCenterOf(blockPos).add(0.0, 1.2, 0.0)
        val d = Random.nextInt(4) / 24.0
        when (state) {
            PlayerState.LOADING -> shriek(level, pos)
            PlayerState.PLAYING -> level.addParticle(
                ParticleTypes.NOTE,
                pos.x + d, pos.y + d, pos.z + d, Random.nextDouble(), 0.0, 0.0)
            else -> Unit
        }
    }

    /**
     * Sends the moving
     *
     * @param level
     * @param state
     * @param position
     * @param dir
     * @param velocity
     */
    fun emitMoving(level: Level, state: PlayerState, position: Vec3, dir: Vec3, velocity: Vec3) {
        val spawn = position.add(dir.scale(0.7 + Random.nextInt(4) / 24.0)).add(velocity)
        when (state) {
            PlayerState.LOADING -> shriek(level, spawn)
            PlayerState.PLAYING -> level.addParticle(
                ParticleTypes.NOTE,
                spawn.x + velocity.x, spawn.y + velocity.y, spawn.z + velocity.z,
                dir.x * 0.5, dir.y * 0.5, dir.z * 0.5)
            else -> Unit
        }
    }
}