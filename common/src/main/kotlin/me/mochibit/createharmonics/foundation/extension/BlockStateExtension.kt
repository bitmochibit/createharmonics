package me.mochibit.createharmonics.extension

import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.core.Direction

/**
 * Returns `true` if property on the receiver [BlockState]
 *
 * @param prop
 * @return `true` if the condition holds, `false` otherwise
 */
fun BlockState.hasProperty(prop: Property<*>): Boolean =
    this.properties.contains(prop)

/**
 * Returns the facing direction on the receiver [BlockState]
 *
 * @return the direction
 */
fun BlockState.getFacingDirection(): Direction =
    when {
        this.hasProperty(BlockStateProperties.FACING) ->
            this.getValue(BlockStateProperties.FACING)

        this.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ->
            this.getValue(BlockStateProperties.HORIZONTAL_FACING)

        else ->
            Direction.NORTH
    }
