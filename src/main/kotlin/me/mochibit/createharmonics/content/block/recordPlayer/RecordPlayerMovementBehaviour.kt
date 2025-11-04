package me.mochibit.createharmonics.content.block.recordPlayer

import com.simibubi.create.api.behaviour.movement.MovementBehaviour
import com.simibubi.create.content.contraptions.behaviour.MovementContext
import me.mochibit.createharmonics.audio.AudioPlayer
import me.mochibit.createharmonics.audio.StreamRegistry
import me.mochibit.createharmonics.audio.effect.*
import me.mochibit.createharmonics.audio.instance.MovingSoundInstance
import me.mochibit.createharmonics.content.item.EtherealRecordItem
import me.mochibit.createharmonics.extension.onClient
import net.minecraft.core.BlockPos
import net.minecraftforge.items.ItemStackHandler
import java.util.*

/**
 * Handles Record Player behavior when attached to a moving contraption.
 * Uses block entity data (synced from server) to determine playback state.
 * Audio streams are managed by AudioPlayer's internal registry using the player block's UUID.
 */
class RecordPlayerMovementBehaviour : MovementBehaviour {


    /**
     * Get the player UUID from the block entity data (server-side)
     */
    private fun getPlayerUUID(context: MovementContext): UUID? {
        val blockEntityData = context.blockEntityData ?: return null
        return if (blockEntityData.contains("playerUUID")) {
            UUID.fromString(blockEntityData.getString("playerUUID"))
        } else {
            null
        }
    }

    /**
     * Get the audio URL from the record in the inventory (server-side data)
     */
    private fun getAudioUrl(context: MovementContext): String? {
        val inventory = getInventoryHandler(context)
        val record = inventory.getStackInSlot(RecordPlayerBlockEntity.RECORD_SLOT)

        if (record.isEmpty || record.item !is EtherealRecordItem) {
            return null
        }

        return EtherealRecordItem.getAudioUrl(record)?.takeIf { it.isNotEmpty() }
    }

    /**
     * Get the inventory handler from the block entity data (server-side)
     */
    private fun getInventoryHandler(context: MovementContext): ItemStackHandler {
        val blockEntityData = context.blockEntityData
        return blockEntityData?.getCompound("inventory")?.let { nbt ->
            val handler = ItemStackHandler(1)
            handler.deserializeNBT(nbt)
            handler
        } ?: ItemStackHandler(1)
    }

    /**
     * Called when the contraption starts moving
     */
    override fun startMoving(context: MovementContext) {
        super.startMoving(context)

        // Start playing on client if there's a disc
        context.world.onClient {
            val audioUrl = getAudioUrl(context)
            val playerUUID = getPlayerUUID(context)

            if (audioUrl != null && playerUUID != null) {
                startClientPlayer(playerUUID, context, audioUrl)
            }
        }
    }

    /**
     * Called when contraption stops - cleanup audio
     */
    override fun stopMoving(context: MovementContext) {
        super.stopMoving(context)

        context.world.onClient {
            val playerUUID = getPlayerUUID(context)
            if (playerUUID != null) {
                stopClientPlayer(playerUUID)
            }
        }
    }

    /**
     * Called every tick - ensure audio is playing if there's a disc
     */
    override fun tick(context: MovementContext) {
        super.tick(context)

        context.world.onClient {
            val playerUUID = getPlayerUUID(context) ?: return@onClient
            val audioUrl = getAudioUrl(context)
            val streamId = playerUUID.toString()

            if (audioUrl == null) return@onClient

            if (!AudioPlayer.isPlaying(streamId)) {
                startClientPlayer(playerUUID, context, audioUrl)
            } else {
                stopClientPlayer(playerUUID)
            }
        }
    }

    /**
     * Start playing audio on client side
     */
    private fun startClientPlayer(playerUUID: UUID, context: MovementContext, audioUrl: String) {
        val streamId = playerUUID.toString()

        AudioPlayer.play(
            audioUrl,
            soundInstanceProvider = { resLoc ->
                MovingSoundInstance(
                    resourceLocation = resLoc,
                    posSupplier = {
                        BlockPos.containing(context.position)
                    },
                    radius = 64
                )
            },
            effectChain = EffectChain(
                listOf(
                    VolumeEffect(0.8f),
                    LowPassFilterEffect(cutoffFrequency = 3000f),
                    ReverbEffect(roomSize = 0.5f, damping = 0.2f, wetMix = 0.8f)
                )
            ),
            streamId = streamId
        )
    }

    /**
     * Stop playing audio on client side
     */
    private fun stopClientPlayer(playerUUID: UUID) {
        AudioPlayer.stopStream(playerUUID.toString())
    }

}