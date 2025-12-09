package me.mochibit.createharmonics.extension

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity

inline fun BlockEntity.onClient(block: () -> Unit) {
    level?.takeIf { it.isClientSide }?.let { block() }
}

inline fun BlockEntity.onServer(block: () -> Unit) {
    level?.takeIf { !it.isClientSide }?.let { block() }
}

inline fun net.minecraft.world.level.Level.onServer(block: (level: ServerLevel) -> Unit) {
    if (!isClientSide) {
        block(this as ServerLevel)
    }
}

inline fun net.minecraft.world.level.Level.onClient(block: (level: ClientLevel) -> Unit) {
    if (isClientSide) {
        block(this as ClientLevel)
    }
}
