package me.mochibit.createharmonics.extension

import net.createmod.ponder.api.level.PonderLevel
import net.minecraft.client.Minecraft
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

inline fun net.minecraft.world.level.Level.onClient(block: (level: ClientLevel, virtual: Boolean) -> Unit) {
    if (!isClientSide) return

    when (this) {
        is ClientLevel -> {
            block(this, false)
        }

        is PonderLevel -> {
            val clientLevel = Minecraft.getInstance().level
            if (clientLevel != null) {
                block(clientLevel, true)
            }
        }
    }
}
