package me.mochibit.createharmonics.content.kinetics.recordPlayer

import com.simibubi.create.AllItems
import com.simibubi.create.AllShapes
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlock
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity
import com.simibubi.create.foundation.block.IBE
import com.simibubi.create.foundation.block.ProperWaterloggedBlock
import me.mochibit.createharmonics.content.kinetics.recordPlayer.andesiteJukebox.AndesiteJukeboxBlockEntity
import me.mochibit.createharmonics.content.records.EtherealRecordItem
import me.mochibit.createharmonics.extension.onServer
import net.createmod.catnip.data.Iterate
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.JukeboxBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import java.util.function.Consumer
import kotlin.math.max

abstract class RecordPlayerBlock(
    properties: Properties,
) : DirectionalKineticBlock(properties),
    IBE<RecordPlayerBlockEntity>,
    ProperWaterloggedBlock {
    companion object {
        private val RANDOM = RandomSource.create()
        val POWERED = BlockStateProperties.POWERED
    }

    init {
        registerDefaultState(
            defaultBlockState()
                .setValue(JukeboxBlock.HAS_RECORD, false)
                .setValue(ProperWaterloggedBlock.WATERLOGGED, false)
                .setValue(POWERED, false),
        )
    }

    override fun getBlockEntityClass(): Class<RecordPlayerBlockEntity> = RecordPlayerBlockEntity::class.java

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

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val pos = context.clickedPos
        val placed = super.getStateForPlacement(context) ?: return null
        return withWater(
            placed.setValue(
                PackagerLinkBlock.POWERED,
                getPower(placed, context.level, pos) > 0,
            ),
            context,
        )
    }

    @Deprecated("Deprecated in Java")
    override fun neighborChanged(
        state: BlockState,
        worldIn: Level,
        pos: BlockPos,
        blockIn: Block,
        fromPos: BlockPos,
        isMoving: Boolean,
    ) {
        if (worldIn.isClientSide) return
        val power = getPower(state, worldIn, pos)
        withBlockEntityDo(
            worldIn,
            pos,
        ) { player: RecordPlayerBlockEntity -> player.playerBehaviour.redstonePowerChanged(power) }
    }

    fun getPower(
        state: BlockState,
        worldIn: Level,
        pos: BlockPos,
    ): Int = worldIn.getBestNeighborSignal(pos)

    @Deprecated("Deprecated in Java")
    override fun updateShape(
        pState: BlockState,
        pDirection: Direction,
        pNeighborState: BlockState,
        pLevel: LevelAccessor,
        pPos: BlockPos,
        pNeighborPos: BlockPos,
    ): BlockState {
        updateWater(pLevel, pState, pPos)
        return pState
    }

    override fun getRotationAxis(state: BlockState): Direction.Axis = state.getValue(FACING).axis

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block?, BlockState?>) {
        super.createBlockStateDefinition(builder)
        builder.add(JukeboxBlock.HAS_RECORD, POWERED, ProperWaterloggedBlock.WATERLOGGED)
    }
}
