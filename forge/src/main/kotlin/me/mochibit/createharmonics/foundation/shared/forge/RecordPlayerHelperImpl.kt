package me.mochibit.createharmonics.foundation.shared.forge

import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlockEntity
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerMovementBehaviour

object RecordPlayerHelperImpl {
    @JvmStatic
    fun onStreamEnd(
        audioPlayerId: String,
        failure: Boolean,
    ): Boolean {
        RecordPlayerBlockEntity.handlePlaybackEnd(audioPlayerId, failure)

        RecordPlayerMovementBehaviour.getContextByPlayerUUID(audioPlayerId)?.let { movementContext ->
            RecordPlayerMovementBehaviour.stopMovingPlayer(movementContext)
        }
        return true
    }

    @JvmStatic
    fun onTitleChange(
        audioPlayerId: String,
        audioName: String,
    ): Boolean {
        RecordPlayerBlockEntity.handleAudioTitleChange(audioPlayerId, audioName)
        return true
    }
}
