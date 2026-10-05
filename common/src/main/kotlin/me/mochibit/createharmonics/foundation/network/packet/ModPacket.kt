package me.mochibit.createharmonics.foundation.network.packet

import kotlinx.serialization.serializer
import me.mochibit.createharmonics.foundation.network.FriendlyByteBufDecoder
import me.mochibit.createharmonics.foundation.network.FriendlyByteBufEncoder
import me.mochibit.createharmonics.foundation.network.NetDirection
import me.mochibit.createharmonics.foundation.services.classScanningService
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player

interface ModPacket
class ServerPacketContext(val player: ServerPlayer)
class ClientPacketContext(val player: Player)

interface C2SPacket : ModPacket {
    fun handle(context: ServerPacketContext)
}

interface S2CPacket : ModPacket {
    fun handle(context: ClientPacketContext)
}

val ModPacket.netDirection: NetDirection
    get() = when (this) {
        is C2SPacket -> if (this is S2CPacket) NetDirection.BOTH else NetDirection.C2S
        is S2CPacket -> NetDirection.S2C
        else -> error("Packet has no direction: ${this::class}")
    }
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AutoPacket(val id: String = "")

object PacketScanner {
    val packetClasses: List<Class<out ModPacket>> by lazy {
        classScanningService.getClassesAnnotatedByWithData(AutoPacket::class.java)
            .map { (clazz, _) ->
                require(ModPacket::class.java.isAssignableFrom(clazz)) {
                    "@AutoPacket class must implement C2SPacket or S2CPacket: $clazz"
                }
                @Suppress("UNCHECKED_CAST")
                clazz as Class<out ModPacket>
            }
    }

    fun idOf(clazz: Class<*>): String =
        clazz.getAnnotation(AutoPacket::class.java)?.id?.takeIf { it.isNotEmpty() }
            ?: clazz.simpleName.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
}