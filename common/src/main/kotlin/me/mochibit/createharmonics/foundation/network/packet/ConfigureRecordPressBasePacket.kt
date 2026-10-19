package me.mochibit.createharmonics.foundation.network.packet

import com.simibubi.create.foundation.utility.AdventureUtil
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import me.mochibit.createharmonics.audio.upload.AudioUploadServer
import me.mochibit.createharmonics.content.processing.recordPressBase.RecordPressBaseBlockEntity
import me.mochibit.createharmonics.content.processing.recordPressBase.RecordPressBaseConfiguration
import me.mochibit.createharmonics.gui.content.recordPressBase.ServerAudioRef
import net.minecraft.core.BlockPos

@AutoPacket
@Serializable
class ConfigureRecordPressBasePacket(
    @Contextual val blockPos: BlockPos,
    val configuration: RecordPressBaseConfiguration,
) : C2SPacket {
    override fun handle(context: ServerPacketContext) {
        val sender = context.player
        if (sender.isSpectator || AdventureUtil.isAdventure(sender)) return
        val world = sender.level()
        if (world == null || !world.isLoaded(blockPos)) return
        if (!sender.canInteractWithBlock(blockPos, 20.0)) return
        val be = world.getBlockEntity(blockPos) as? RecordPressBaseBlockEntity ?: return

        val senderName = sender.gameProfile.name
        val previous = be.configuration.entries.associateBy { it.url }
        val entries = configuration.entries.mapNotNull { it.sanitized(senderName, previous) }

        be.configuration = configuration.copy(
            entries = entries,
            currentUrlIndex = configuration.currentUrlIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0)),
        )
        be.sendData()
        be.setChanged()
    }

    private fun RecordPressBaseConfiguration.AudioEntry.sanitized(
        sender: String,
        previous: Map<String, RecordPressBaseConfiguration.AudioEntry>,
    ): RecordPressBaseConfiguration.AudioEntry? {
        val safeWeight = if (weight.isFinite()) weight.coerceIn(0f, 1f) else 1f
        val ref = ServerAudioRef.parse(url) ?: return copy(weight = safeWeight, name = null)

        val known = previous[url]?.name
        val resolved =
            if (ref.owner == sender) {
                AudioUploadServer.list(ref.owner)
                    .firstOrNull { it.fileId == ref.fileId }?.displayName ?: known
            } else known

        return resolved?.let { copy(weight = safeWeight, name = it) }
    }
}