package me.mochibit.createharmonics.content.kinetics.recordPlayer

import com.simibubi.create.AllItems
import com.simibubi.create.AllShapes
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock
import me.mochibit.createharmonics.content.kinetics.recordPlayer.andesiteJukebox.AndesiteJukeboxBlockEntity
import me.mochibit.createharmonics.content.records.EtherealRecordItem
import me.mochibit.createharmonics.extension.onServer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.JukeboxBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

abstract class RecordPlayerBlock(
    properties: Properties,
) : DirectionalKineticBlock(properties) {
    companion object {
        private val RANDOM = RandomSource.create()
    }

    init {
        registerDefaultState(
            stateDefinition
                .any()
                .setValue(JukeboxBlock.HAS_RECORD, false),
        )
    }

    @Deprecated("Deprecated in Java")
    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape = AllShapes.CASING_14PX[Direction.DOWN]

    @Deprecated("Deprecated in Java")
    override fun use(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pPlayer: Player,
        pHand: InteractionHand,
        pHit: BlockHitResult,
    ): InteractionResult {
        val blockEntity = pLevel.getBlockEntity(pPos) as? AndesiteJukeboxBlockEntity ?: return InteractionResult.PASS
        val clickItem = pPlayer.getItemInHand(pHand)

        if (AllItems.WRENCH.isIn(clickItem)) {
            return InteractionResult.PASS
        }

        // Only allow interactions on the FACING direction (the head/slot side)
        val facing = pState.getValue(FACING)
        if (pHit.direction != facing) {
            return InteractionResult.PASS
        }

        if (!clickItem.isEmpty && clickItem.item !is EtherealRecordItem) {
            return InteractionResult.PASS
        }

        pLevel.onServer {
            if (clickItem.item is EtherealRecordItem && !blockEntity.playerBehaviour.hasRecord()) {
                // Click with record: insert and play
                blockEntity.playerBehaviour.insertRecord(clickItem)
                clickItem.shrink(1)

                // Play insertion sound
                pLevel.playSound(
                    null,
                    pPos,
                    SoundEvents.ITEM_FRAME_ADD_ITEM,
                    SoundSource.PLAYERS,
                    0.2f,
                    1f + RANDOM.nextFloat(),
                )

                return InteractionResult.SUCCESS
            }

            if (clickItem.isEmpty && blockEntity.playerBehaviour.hasRecord()) {
                // Click with empty hand: remove record
                val disc = blockEntity.playerBehaviour.popRecord() ?: return@onServer
                pPlayer.addItem(disc)

                blockEntity.playerBehaviour.stopPlayer()

                // Play removal sound
                pLevel.playSound(
                    null,
                    pPos,
                    SoundEvents.ITEM_PICKUP,
                    SoundSource.PLAYERS,
                    0.2f,
                    1f + RANDOM.nextFloat(),
                )

                return InteractionResult.SUCCESS
            }
        }

        return InteractionResult.SUCCESS
    }

    override fun hasShaftTowards(
        world: LevelReader?,
        pos: BlockPos?,
        state: BlockState,
        face: Direction?,
    ): Boolean = face == state.getValue(FACING).opposite

    override fun getRotationAxis(state: BlockState): Direction.Axis = state.getValue(FACING).axis

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block?, BlockState?>) {
        super.createBlockStateDefinition(builder)
        builder.add(JukeboxBlock.HAS_RECORD)
    }
}
