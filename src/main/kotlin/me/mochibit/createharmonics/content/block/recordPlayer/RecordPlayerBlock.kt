package me.mochibit.createharmonics.content.block.recordPlayer

import com.simibubi.create.AllItems
import com.simibubi.create.AllShapes
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock
import me.mochibit.createharmonics.content.block.recordPlayer.andesiteJukebox.AndesiteJukeboxBlockEntity
import me.mochibit.createharmonics.content.item.EtherealRecordItem
import me.mochibit.createharmonics.extension.onServer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
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

        if (!clickItem.isEmpty && clickItem.item !is EtherealRecordItem) {
            return InteractionResult.PASS
        }

        pLevel.onServer {
            val isPowered = pLevel.hasNeighborSignal(pPos)

            if (pPlayer.isShiftKeyDown) {
                if (!blockEntity.hasRecord()) {
                    return InteractionResult.PASS
                }

                val disc = blockEntity.popRecord() ?: return@onServer
                pPlayer.addItem(disc)

                blockEntity.stopPlayer()

                return InteractionResult.SUCCESS
            }

            if (clickItem.item is EtherealRecordItem && !blockEntity.hasRecord()) {
                blockEntity.insertRecord(clickItem)
                clickItem.shrink(1)
                return InteractionResult.SUCCESS
            }

            if (blockEntity.hasRecord() && !isPowered) {
                when (blockEntity.playbackState) {
                    RecordPlayerBehaviour.PlaybackState.PLAYING -> blockEntity.pausePlayer()
                    else -> blockEntity.startPlayer()
                }
            }
        }

        return InteractionResult.SUCCESS
    }

    override fun onRemove(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pNewState: BlockState,
        pIsMoving: Boolean,
    ) {
        super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving)
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
