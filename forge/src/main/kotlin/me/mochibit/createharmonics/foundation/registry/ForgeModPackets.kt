package me.mochibit.createharmonics.foundation.registry

import me.mochibit.createharmonics.foundation.extension.asResource
import me.mochibit.createharmonics.foundation.network.packet.C2SPacket
import me.mochibit.createharmonics.foundation.network.packet.ModPacket
import me.mochibit.createharmonics.foundation.network.packet.S2CPacket
import me.mochibit.createharmonics.foundation.network.packet.readPacket
import me.mochibit.createharmonics.foundation.network.packet.writeTo
import net.minecraftforge.network.NetworkDirection
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.simple.SimpleChannel

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

    fun registerPacket(packet: ModPacket) {
        val direction: Int =
            (if (packet is C2SPacket) 1 else 0) or
                (if (packet is S2CPacket) 2 else 0)

        if (direction == 0) throw IllegalArgumentException("Packet must implement either C2SPacket or S2CPacket !! $packet")

        if (direction == 3) {
            registerMessage(packet, NetworkDirection.PLAY_TO_SERVER)
            registerMessage(packet, NetworkDirection.PLAY_TO_CLIENT)
            return
        }

        registerMessage(
            packet,
            if (direction == 1) NetworkDirection.PLAY_TO_SERVER else NetworkDirection.PLAY_TO_CLIENT,
        )
    }

    private fun registerMessage(
        messageType: ModPacket,
        direction: NetworkDirection,
    ) {
        channel.registerMessage(
            globalPacketId++,
            messageType.javaClass,
            { packet, buf -> packet.writeTo(buf) },
            { buf -> buf.readPacket() },
            { packet, ctx ->
                val context = ModPacket.Context(ctx.get().sender)
                when (direction) {
                    NetworkDirection.PLAY_TO_SERVER -> {
                        packet.handle(context)
                        if (packet is C2SPacket) packet.handleClient(context)
                    }

                    NetworkDirection.PLAY_TO_CLIENT -> {
                        packet.handle(context)
                        if (packet is S2CPacket) packet.handleServer(context)
                    }

                    else -> {
                        packet.handle(context)
                    }
                }

                ctx.get().packetHandled = true
            },
        )
    }

    override fun register() {
        ModPackets.packets.forEach {
            registerPacket(it)
        }
    }
}
