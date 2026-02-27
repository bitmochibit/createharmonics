package me.mochibit.createharmonics.foundation.registry

import me.mochibit.createharmonics.foundation.info
import me.mochibit.createharmonics.foundation.network.packet.ModPacket
import me.mochibit.createharmonics.foundation.services.NetworkService
import me.mochibit.createharmonics.foundation.services.networkService

object ModPackets : Registrable, NetworkService by networkService {
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
}
