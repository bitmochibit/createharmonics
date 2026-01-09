package me.mochibit.createharmonics.registry

import com.simibubi.create.foundation.networking.SimplePacketBase
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.asResource
import me.mochibit.createharmonics.network.packet.AudioPlayerContextStopPacket
import me.mochibit.createharmonics.network.packet.AudioPlayerStreamEndPacket
import me.mochibit.createharmonics.network.packet.ConfigureRecordPressBasePacket
import me.mochibit.createharmonics.network.packet.ContraptionBlockDataChangedPacket
import me.mochibit.createharmonics.network.packet.UpdateAudioNamePacket
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.network.NetworkDirection
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.simple.SimpleChannel
import java.util.function.BiConsumer
import java.util.function.Supplier

object ModPackets : AutoRegistrable {
    private const val PROTOCOL_VERSION = "1"
    private const val NETWORK_VERSION = "1"

    val channel: SimpleChannel =
        NetworkRegistry.ChannelBuilder
            .named("main".asResource())
            .serverAcceptedVersions { it == NETWORK_VERSION }
            .clientAcceptedVersions { it == NETWORK_VERSION }
            .networkProtocolVersion { PROTOCOL_VERSION }
            .simpleChannel()

    private val clientToServer =
        listOf(
            packet(::AudioPlayerStreamEndPacket),
            packet(::ConfigureRecordPressBasePacket),
            packet(::UpdateAudioNamePacket),
        )

    private val serverToClient =
        listOf(
            packet(::AudioPlayerContextStopPacket),
            packet(::ContraptionBlockDataChangedPacket),
        )

    override fun register(
        eventBus: IEventBus,
        context: FMLJavaModLoadingContext,
    ) {
        var id = 0
        clientToServer.forEach { it.register(channel, id++, NetworkDirection.PLAY_TO_SERVER) }
        serverToClient.forEach { it.register(channel, id++, NetworkDirection.PLAY_TO_CLIENT) }
    }
}

private class PacketType<T : SimplePacketBase>(
    val clazz: Class<T>,
    val factory: (FriendlyByteBuf) -> T,
) {
    fun register(
        channel: SimpleChannel,
        id: Int,
        direction: NetworkDirection,
    ) {
        channel
            .messageBuilder(clazz, id, direction)
            .encoder { packet, buffer -> packet.write(buffer) }
            .decoder(factory)
            .consumerNetworkThread(
                BiConsumer { packet: T, ctxSupplier: Supplier<NetworkEvent.Context> ->
                    ctxSupplier.get().apply {
                        if (packet.handle(this)) {
                            packetHandled = true
                        }
                    }
                },
            ).add()
    }
}

private inline fun <reified T : SimplePacketBase> packet(noinline factory: (FriendlyByteBuf) -> T) = PacketType(T::class.java, factory)
