package me.mochibit.createharmonics.network

import me.mochibit.createharmonics.audio.AudioPlayer
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier


class RemoveModAudioPlayerPacket(val resourceLocationToRemove: ResourceLocation) {
    companion object {
        /**
         * Encodes the packet data to the buffer
         */
        @JvmStatic
        fun encode(msg: RemoveModAudioPlayerPacket, buf: FriendlyByteBuf) {
            buf.writeResourceLocation(msg.resourceLocationToRemove)
        }

        /**
         * Decodes the packet data from the buffer
         */
        @JvmStatic
        fun decode(buf: FriendlyByteBuf): RemoveModAudioPlayerPacket {
            return RemoveModAudioPlayerPacket(buf.readResourceLocation())
        }

        /**
         * Handles the packet on the client side
         */
        @JvmStatic
        fun handle(msg: RemoveModAudioPlayerPacket, ctx: Supplier<NetworkEvent.Context>) {
            ctx.get().enqueueWork {
                AudioPlayer.stopStream(msg.resourceLocationToRemove)
            }
            ctx.get().packetHandled = true
        }
    }
}