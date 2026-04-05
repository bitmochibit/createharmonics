package me.mochibit.createharmonics.content.kinetics.recordPlayer

import com.simibubi.create.AllItems
import me.mochibit.createharmonics.content.records.EtherealRecordItem
import me.mochibit.createharmonics.foundation.extension.onServer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.phys.BlockHitResult

interface RecordPlayerTrait {
    fun getRecordPlayerBlockEntity(
        level: Level,
        pos: BlockPos,
    ): RecordPlayerBlockEntity? = level.getBlockEntity(pos) as? RecordPlayerBlockEntity

    fun handleRecordUse(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult,
        facing: Direction,
    ): ItemInteractionResult {
        val blockEntity =
            level.getBlockEntity(pos) as? RecordPlayerBlockEntity ?: return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        val clickItem = player.getItemInHand(hand)

        if (AllItems.WRENCH.isIn(clickItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        }

        // Only allow interactions on the FACING direction (the head/slot side)
        if (hit.direction != facing) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        }

        if (!clickItem.isEmpty && clickItem.item !is EtherealRecordItem) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        }

        level.onServer {
            if (clickItem.item is EtherealRecordItem && !blockEntity.playerBehaviour.hasRecord()) {
                // Click with record: insert and play
                blockEntity.playerBehaviour.insertRecord(clickItem)
                clickItem.shrink(1)

                // Play insertion sound
                level.playSound(
                    null,
                    pos,
                    SoundEvents.ITEM_FRAME_ADD_ITEM,
                    SoundSource.PLAYERS,
                    0.2f,
                    1f + RandomSource.create().nextFloat(),
                )

                return ItemInteractionResult.SUCCESS
            }

            if (clickItem.isEmpty && blockEntity.playerBehaviour.hasRecord()) {
                // Click with empty hand: remove record
                val disc = blockEntity.playerBehaviour.popRecord() ?: return@onServer
                player.addItem(disc)

                blockEntity.playerBehaviour.stopPlayer()

                // Play removal sound
                level.playSound(
                    null,
                    pos,
                    SoundEvents.ITEM_PICKUP,
                    SoundSource.PLAYERS,
                    0.2f,
                    1f + RandomSource.create().nextFloat(),
                )

                return ItemInteractionResult.SUCCESS
            }
        }

        return ItemInteractionResult.SUCCESS
    }

    companion object {
        val HAS_ETHEREAL_RECORD: BooleanProperty = BooleanProperty.create("has_ethereal_record")
    }
}
