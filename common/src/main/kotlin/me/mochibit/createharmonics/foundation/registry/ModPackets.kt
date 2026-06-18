package me.mochibit.createharmonics.foundation.registry

import me.mochibit.createharmonics.foundation.info
import me.mochibit.createharmonics.foundation.network.packet.ModPacket
import me.mochibit.createharmonics.foundation.services.NetworkService
import me.mochibit.createharmonics.foundation.services.networkService
import net.minecraft.core.Registry
import kotlin.reflect.KClass

object ModPackets : CommonRegistry, NetworkService by networkService {
    val packetClasses: List<KClass<out ModPacket>> =
        mutableListOf<KClass<out ModPacket>>().apply {
            fun collectSubclasses(kClass: KClass<out ModPacket>) {
                val subs = kClass.sealedSubclasses
                if (subs.isEmpty()) {
                    add(kClass)
                } else {
                    subs.forEach { collectSubclasses(it) }
                }
            }
            collectSubclasses(ModPacket::class)
        }

    override fun register(registry: Registry<in Any>?) {
        "Loading Mod Packets".info()
    }
}
