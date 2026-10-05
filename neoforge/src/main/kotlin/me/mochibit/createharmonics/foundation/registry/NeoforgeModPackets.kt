package me.mochibit.createharmonics.foundation.registry

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import me.mochibit.createharmonics.CreateHarmonicsMod.MOD_ID
import me.mochibit.createharmonics.foundation.extension.asResource
import me.mochibit.createharmonics.foundation.network.FriendlyByteBufDecoder
import me.mochibit.createharmonics.foundation.network.FriendlyByteBufEncoder
import me.mochibit.createharmonics.foundation.network.packet.C2SPacket
import me.mochibit.createharmonics.foundation.network.packet.ClientPacketContext
import me.mochibit.createharmonics.foundation.network.packet.ModPacket
import me.mochibit.createharmonics.foundation.network.packet.PacketScanner
import me.mochibit.createharmonics.foundation.network.packet.S2CPacket
import me.mochibit.createharmonics.foundation.network.packet.ServerPacketContext
import net.minecraft.core.Registry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler
import net.neoforged.neoforge.network.handling.IPayloadHandler
import net.neoforged.neoforge.network.registration.PayloadRegistrar
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.starProjectedType

class ModPacketPayload<T : ModPacket>(
    val packet: T,
    private val payloadType: CustomPacketPayload.Type<ModPacketPayload<T>>,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ModPacketPayload<T>> = payloadType
}

object NeoforgeModPackets {
    private class Entry<T : ModPacket>(
        val type: CustomPacketPayload.Type<ModPacketPayload<T>>,
    )
    private val entries = mutableMapOf<Class<out ModPacket>, Entry<*>>()

    fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1").optional()
        PacketScanner.packetClasses.forEach { registerPacket(registrar, it) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : ModPacket> registerPacket(registrar: PayloadRegistrar, clazz: Class<T>) {
        val isC2S = C2SPacket::class.java.isAssignableFrom(clazz)
        val isS2C = S2CPacket::class.java.isAssignableFrom(clazz)
        require(isC2S || isS2C) { "Packet must implement C2SPacket or S2CPacket: $clazz" }

        val serializer = serializer(clazz) as KSerializer<T>
        val type = CustomPacketPayload.Type<ModPacketPayload<T>>(
            ResourceLocation.fromNamespaceAndPath(MOD_ID, PacketScanner.idOf(clazz)),
        )
        val codec = StreamCodec.of<RegistryFriendlyByteBuf, ModPacketPayload<T>>(
            { buf, p -> serializer.serialize(FriendlyByteBufEncoder(buf), p.packet) },
            { buf -> ModPacketPayload(serializer.deserialize(FriendlyByteBufDecoder(buf)), type) },
        )
        entries[clazz] = Entry(type)

        val toServer = IPayloadHandler<ModPacketPayload<T>> { p, ctx ->
            (p.packet as C2SPacket).handle(ServerPacketContext(ctx.player() as ServerPlayer))
        }
        val toClient = IPayloadHandler<ModPacketPayload<T>> { p, ctx ->
            (p.packet as S2CPacket).handle(ClientPacketContext(ctx.player()))
        }

        when {
            isC2S && isS2C -> registrar.playBidirectional(type, codec, DirectionalPayloadHandler(toClient, toServer))
            isC2S -> registrar.playToServer(type, codec, toServer)
            else -> registrar.playToClient(type, codec, toClient)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : ModPacket> payloadFor(packet: T): ModPacketPayload<T> {
        val entry = entries[packet.javaClass] as? Entry<T>
            ?: error("Unregistered packet class (missing @AutoPacket?): ${packet.javaClass}")
        return ModPacketPayload(packet, entry.type)
    }
}
