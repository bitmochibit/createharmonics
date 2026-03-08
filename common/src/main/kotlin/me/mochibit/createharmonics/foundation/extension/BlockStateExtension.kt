package me.mochibit.createharmonics.extension

import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.core.Direction

fun BlockState.hasProperty(prop: Property<*>): Boolean =
    this.properties.contains(prop)

fun BlockState.getFacingDirection(): Direction =
    when {
        this.hasProperty(BlockStateProperties.FACING) ->
            this.getValue(BlockStateProperties.FACING)

        this.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ->
            this.getValue(BlockStateProperties.HORIZONTAL_FACING)

        else ->
            Direction.NORTH
    }
