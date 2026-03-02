package me.mochibit.createharmonics.foundation.registry

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import me.mochibit.createharmonics.foundation.extension.asResource
import me.mochibit.createharmonics.foundation.network.FriendlyByteBufDecoder
import me.mochibit.createharmonics.foundation.network.FriendlyByteBufEncoder
import me.mochibit.createharmonics.foundation.network.packet.C2SPacket
import me.mochibit.createharmonics.foundation.network.packet.ModPacket
import me.mochibit.createharmonics.foundation.network.packet.S2CPacket
import net.minecraftforge.network.NetworkDirection
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.simple.SimpleChannel
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.starProjectedType

object ForgeModPackets : Registrable, ForgeRegistry {
    private const val PROTOCOL_VERSION = "1"
    private const val NETWORK_VERSION = "1"

    private var globalPacketId = 0

    val channel: SimpleChannel =
        NetworkRegistry.ChannelBuilder
            .named("main".asResource())
            .serverAcceptedVersions { it == NETWORK_VERSION }
            .clientAcceptedVersions { it == NETWORK_VERSION }
            .networkProtocolVersion { PROTOCOL_VERSION }
            .simpleChannel()

    fun registerPacket(packetClass: KClass<out ModPacket>) {
        val isC2S = packetClass.isSubclassOf(C2SPacket::class)
        val isS2C = packetClass.isSubclassOf(S2CPacket::class)

        if (!isC2S && !isS2C) throw IllegalArgumentException("Packet must implement either C2SPacket or S2CPacket !! $packetClass")

        if (isC2S && isS2C) {
            registerMessage(packetClass, NetworkDirection.PLAY_TO_SERVER)
            registerMessage(packetClass, NetworkDirection.PLAY_TO_CLIENT)
            return
        }

        registerMessage(
            packetClass,
            if (isC2S) NetworkDirection.PLAY_TO_SERVER else NetworkDirection.PLAY_TO_CLIENT,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : ModPacket> registerMessage(
        packetClass: KClass<T>,
        direction: NetworkDirection,
    ) {
        val serializer = serializer(packetClass.starProjectedType) as KSerializer<T>

        channel.registerMessage(
            globalPacketId++,
            packetClass.java,
            { packet, buf -> serializer.serialize(FriendlyByteBufEncoder(buf), packet) },
            { buf -> serializer.deserialize(FriendlyByteBufDecoder(buf)) },
            { packet, ctx ->
                val context = ModPacket.Context(ctx.get().sender)
                ctx.get().enqueueWork {
                    when (direction) {
                        NetworkDirection.PLAY_TO_SERVER -> {
                            packet.handle(context)
                        }

                        NetworkDirection.PLAY_TO_CLIENT -> {
                            packet.handle(context)
                            if (packet is S2CPacket) packet.handleServer(context)
                        }

                        else -> {
                            packet.handle(context)
                        }
                    }
                }

                ctx.get().packetHandled = true
            },
        )
    }

    override fun register() {
        ModPackets.packetClasses.forEach {
            registerPacket(it)
        }
    }
}
