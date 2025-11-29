package me.mochibit.createharmonics.network.packet

import com.simibubi.create.foundation.networking.SimplePacketBase
import me.mochibit.createharmonics.audio.AudioPlayerRegistry
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent

class LobbyJoinedPacket(
    val lobbyStarted: Boolean,
    val audioPlayerId: String,
    val playTime: Long,
) : SimplePacketBase() {
    constructor(buffer: FriendlyByteBuf) : this(
        lobbyStarted = buffer.readBoolean(),
        audioPlayerId = buffer.readUtf(),
        playTime = buffer.readLong()
    )

    override fun write(buffer: FriendlyByteBuf) {
        buffer.writeBoolean(lobbyStarted)
        buffer.writeUtf(audioPlayerId)
        buffer.writeLong(playTime)
    }

    override fun handle(context: NetworkEvent.Context): Boolean {
        context.enqueueWork {
            val audioPlayer = AudioPlayerRegistry.getPlayer(audioPlayerId) ?: return@enqueueWork
            // Convert milliseconds to seconds for startAudio
            audioPlayer.startAudio(playTime / 1000.0)
        }
        return true
    }
}