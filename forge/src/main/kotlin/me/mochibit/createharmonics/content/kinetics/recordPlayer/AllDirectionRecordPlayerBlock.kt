package me.mochibit.createharmonics.content.kinetics.recordPlayer

<<<<<<<< HEAD:forge/src/main/kotlin/me/mochibit/createharmonics/content/kinetics/recordPlayer/RecordPlayerBlock.kt
import com.simibubi.create.AllItems
========
>>>>>>>> brass-jukebox:forge/src/main/kotlin/me/mochibit/createharmonics/content/kinetics/recordPlayer/AllDirectionRecordPlayerBlock.kt
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlock
import com.simibubi.create.foundation.block.IBE
import com.simibubi.create.foundation.block.ProperWaterloggedBlock
<<<<<<<< HEAD:forge/src/main/kotlin/me/mochibit/createharmonics/content/kinetics/recordPlayer/RecordPlayerBlock.kt
import me.mochibit.createharmonics.content.kinetics.recordPlayer.andesiteJukebox.AndesiteJukeboxBlockEntity
import me.mochibit.createharmonics.content.record.EtherealRecordItem
import me.mochibit.createharmonics.foundation.extension.onServer
========
>>>>>>>> brass-jukebox:forge/src/main/kotlin/me/mochibit/createharmonics/content/kinetics/recordPlayer/AllDirectionRecordPlayerBlock.kt
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.FluidTags
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.JukeboxBlock
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.BlockHitResult

abstract class AllDirectionRecordPlayerBlock(
    properties: Properties,
) : DirectionalKineticBlock(properties),
<<<<<<<< HEAD:forge/src/main/kotlin/me/mochibit/createharmonics/content/kinetics/recordPlayer/RecordPlayerBlock.kt
    IBE<RecordPlayerBlockEntity> {
========
    IBE<RecordPlayerBlockEntity>,
    ProperWaterloggedBlock, RecordPlayerTrait{
>>>>>>>> brass-jukebox:forge/src/main/kotlin/me/mochibit/createharmonics/content/kinetics/recordPlayer/AllDirectionRecordPlayerBlock.kt
    companion object {
        private val RANDOM = RandomSource.create()
        val POWERED = BlockStateProperties.POWERED
    }

    init {
        registerDefaultState(
            defaultBlockState()
                .setValue(JukeboxBlock.HAS_RECORD, false)
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
        return handleRecordUse(pState, pLevel, pPos, pPlayer, pHand, pHit, pState.getValue(FACING))
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
<<<<<<<< HEAD:forge/src/main/kotlin/me/mochibit/createharmonics/content/kinetics/recordPlayer/RecordPlayerBlock.kt
        return placed.setValue(
            PackagerLinkBlock.POWERED,
            getPower(placed, context.level, pos) > 0,
========
        return withWater(
            placed.setValue(
                PackagerLinkBlock.POWERED,
                context.level.getBestNeighborSignal(pos) > 0,
            ),
            context,
>>>>>>>> brass-jukebox:forge/src/main/kotlin/me/mochibit/createharmonics/content/kinetics/recordPlayer/AllDirectionRecordPlayerBlock.kt
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
        val power = worldIn.getBestNeighborSignal(pos)
        val isUnderwater = worldIn.getFluidState(pos).`is`(FluidTags.WATER)

        withBlockEntityDo(
            worldIn,
            pos,
        ) { player: RecordPlayerBlockEntity ->
            player.playerBehaviour.redstonePowerChanged(power)
            if (isUnderwater) {
                player.sendData()
            }
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun updateShape(
        pState: BlockState,
        pDirection: Direction,
        pNeighborState: BlockState,
        pLevel: LevelAccessor,
        pPos: BlockPos,
        pNeighborPos: BlockPos,
    ): BlockState = pState

    override fun getRotationAxis(state: BlockState): Direction.Axis = state.getValue(FACING).axis

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block?, BlockState?>) {
        super.createBlockStateDefinition(builder)
        builder.add(JukeboxBlock.HAS_RECORD, POWERED, ProperWaterloggedBlock.WATERLOGGED)
    }
}
