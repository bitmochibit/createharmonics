package me.mochibit.createharmonics.foundation.registry

import me.mochibit.createharmonics.foundation.info
import me.mochibit.createharmonics.foundation.network.ModPacket
import net.minecraft.server.level.ServerPlayer

object ModPackets : Registrable {
    val packets: List<ModPacket> =
        mutableListOf<ModPacket>().apply {
            ModPacket::class.sealedSubclasses.forEach { subclass ->
                if (ModPacket::class.java.isAssignableFrom(subclass.java)) {
                    val instance = subclass.objectInstance ?: return@forEach
                    add(instance)
                }
            }
        }

    override fun register() {
        "Loading Mod Packets".info()
    }

    fun sendToServer(packet: ModPacket) {
    }

    fun sendToPlayer(
        player: ServerPlayer,
        packet: ModPacket,
    ) {
    }
}
