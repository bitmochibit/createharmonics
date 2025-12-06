package me.mochibit.createharmonics.network

import com.simibubi.create.foundation.networking.SimplePacketBase
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.asResource
import me.mochibit.createharmonics.network.packet.AudioPlayerContextStopPacket
import me.mochibit.createharmonics.network.packet.AudioPlayerStreamEndPacket
import me.mochibit.createharmonics.network.packet.ContraptionBlockDataChangedPacket
import me.mochibit.createharmonics.registry.AbstractModRegistry
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.network.NetworkDirection
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.simple.SimpleChannel
import java.util.function.Supplier

object ModNetworkHandler : AbstractModRegistry {
    private const val PROTOCOL_VERSION = "1"
    private const val NETWORK_VERSION = "1"
    private var packetId = 0

    val channel: SimpleChannel =
        NetworkRegistry.ChannelBuilder
            .named(
                "main".asResource(),
            ).serverAcceptedVersions { it == NETWORK_VERSION }
            .clientAcceptedVersions { it == NETWORK_VERSION }
            .networkProtocolVersion { PROTOCOL_VERSION }
            .simpleChannel()

    override fun register(
        eventBus: IEventBus,
        context: FMLJavaModLoadingContext,
    ) {
        Logger.info("Registering Mod Network Channel")
        val allPackets = PacketType::class.sealedSubclasses.mapNotNull { it.objectInstance }
        for (packet in allPackets) {
            packet.register()
        }
    }

    private sealed class PacketType<T : SimplePacketBase>(
        val type: Class<T>,
        factory: (FriendlyByteBuf) -> T,
        val direction: NetworkDirection,
    ) {
        private val encoder: (T, FriendlyByteBuf) -> Unit = { base, buff ->
            base.write(buff)
        }
        private val decoder: (FriendlyByteBuf) -> T = factory
        private val handler: (T, Supplier<NetworkEvent.Context>) -> Unit = { base, ctxSupplier ->
            val ctx = ctxSupplier.get()
            if (base.handle(ctx)) {
                ctx.packetHandled = true
            }
        }

        fun register() {
            channel
                .messageBuilder(type, packetId++, direction)
                .encoder(encoder)
                .decoder(decoder)
                .consumerNetworkThread(handler)
                .add()
        }

        // CLIENT -> SERVER
        object AudioPlayerStreamEnd : PacketType<AudioPlayerStreamEndPacket>(
            AudioPlayerStreamEndPacket::class.java,
            ::AudioPlayerStreamEndPacket,
            NetworkDirection.PLAY_TO_SERVER,
        )

        // SERVER -> CLIENT
        object AudioPlayerContextStop : PacketType<AudioPlayerContextStopPacket>(
            AudioPlayerContextStopPacket::class.java,
            ::AudioPlayerContextStopPacket,
            NetworkDirection.PLAY_TO_CLIENT,
        )

        object ContraptionBlockDataChanged : PacketType<ContraptionBlockDataChangedPacket>(
            ContraptionBlockDataChangedPacket::class.java,
            ::ContraptionBlockDataChangedPacket,
            NetworkDirection.PLAY_TO_CLIENT,
        )
    }
}
