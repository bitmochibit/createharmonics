package me.mochibit.createharmonics.extension

import net.minecraft.world.level.block.entity.BlockEntity

inline fun BlockEntity.onClient(block: () -> Unit) {
    level?.takeIf { it.isClientSide }?.let { block() }
}

inline fun BlockEntity.onServer(block: () -> Unit) {
    level?.takeIf { !it.isClientSide }?.let { block() }
}

inline fun net.minecraft.world.level.Level.onClient(block: () -> Unit) {
    if (isClientSide) block()
}

inline fun net.minecraft.world.level.Level.onServer(block: () -> Unit) {
    if (!isClientSide) block()
}