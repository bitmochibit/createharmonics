package me.mochibit.createharmonics.foundation.extension

import me.mochibit.createharmonics.foundation.services.PlatformService
import me.mochibit.createharmonics.foundation.services.platformService
import net.createmod.ponder.api.level.PonderLevel
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity

/**
 * On client on the receiver [BlockEntity]
 *
 * @param block
 * @receiver
 */
inline fun BlockEntity.onClient(block: () -> Unit) {
    level?.takeIf { it.isClientSide }?.let { block() }
}

/**
 * On server on the receiver [BlockEntity]
 *
 * @param block
 * @receiver
 */
inline fun BlockEntity.onServer(block: () -> Unit) {
    level?.takeIf { !it.isClientSide }?.let { block() }
}

/**
 * On server on the receiver [Level]
 *
 * @param block
 * @receiver
 */
inline fun Level.onServer(block: (level: ServerLevel) -> Unit) {
    if (!isClientSide) {
        block(this as ServerLevel)
    }
}

/**
 * On client on the receiver [Level]
 *
 * @param block
 * @receiver
 */
inline fun Level.onClient(block: (level: ClientLevel, virtual: Boolean) -> Unit) {
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
