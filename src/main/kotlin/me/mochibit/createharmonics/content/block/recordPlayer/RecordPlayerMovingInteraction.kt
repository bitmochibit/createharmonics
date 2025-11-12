package me.mochibit.createharmonics.content.block.recordPlayer

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour
import com.simibubi.create.content.contraptions.AbstractContraptionEntity
import com.simibubi.create.content.contraptions.behaviour.MovementContext
import me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerBlockEntity.Companion.RECORD_SLOT
import me.mochibit.createharmonics.content.item.EtherealRecordItem
import me.mochibit.createharmonics.extension.onServer
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import org.apache.commons.lang3.tuple.MutablePair

class RecordPlayerMovingInteraction : MovingInteractionBehaviour() {
    override fun handlePlayerInteraction(
        player: Player,
        activeHand: InteractionHand,
        localPos: BlockPos,
        contraptionEntity: AbstractContraptionEntity
    ): Boolean {
        val actor: MutablePair<StructureTemplate.StructureBlockInfo, MovementContext> =
            contraptionEntity.contraption?.getActorAt(localPos) ?: return false

        val context = actor.right
        context.world.onServer {
            val storageManager = contraptionEntity.contraption.storage
            val rpInventory =
                storageManager.allItemStorages[localPos] as? RecordPlayerMountedStorage ?: return@onServer

            // If shift click, remove the record, if click with record item, insert it
            val clickItem = player.getItemInHand(activeHand)
            if (player.isShiftKeyDown) {
                val discItem = rpInventory.getStackInSlot(RECORD_SLOT)
                if (discItem.isEmpty) return@onServer
                val item = rpInventory.extractItem(RECORD_SLOT, 1, false)
                if (!player.inventory.add(item)) {
                    player.drop(item, false)
                }
                rpInventory.markDirty()
            } else {
                val discItem = rpInventory.getStackInSlot(RECORD_SLOT)
                if (!discItem.isEmpty) return@onServer
                if (clickItem.item is EtherealRecordItem) {
                    rpInventory.insertItem(RECORD_SLOT, clickItem.copy(), false)
                    clickItem.shrink(1)
                    rpInventory.markDirty()
                }
            }
        }
        return true
    }


}