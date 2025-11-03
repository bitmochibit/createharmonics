package me.mochibit.createharmonics.content.block.recordPlayer

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour
import com.simibubi.create.content.contraptions.AbstractContraptionEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player

class RecordPlayerMovingInteraction: MovingInteractionBehaviour() {
    override fun handlePlayerInteraction(
        player: Player?,
        activeHand: InteractionHand?,
        localPos: BlockPos?,
        contraptionEntity: AbstractContraptionEntity?
    ): Boolean {
        return super.handlePlayerInteraction(player, activeHand, localPos, contraptionEntity)
    }
}