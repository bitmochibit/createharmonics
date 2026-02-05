package me.mochibit.createharmonics.content.processing.recordPressBase

import com.simibubi.create.AllBlocks
import com.simibubi.create.AllItems
import com.simibubi.create.AllShapes
import com.simibubi.create.AllSoundEvents
import com.simibubi.create.Create
import com.simibubi.create.content.equipment.wrench.IWrenchable
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack
import com.simibubi.create.content.logistics.depot.SharedDepotBlockMethods
import com.simibubi.create.foundation.block.IBE
import com.simibubi.create.foundation.block.ProperWaterloggedBlock
import com.simibubi.create.foundation.block.ProperWaterloggedBlock.WATERLOGGED
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import me.mochibit.createharmonics.registry.ModBlockEntities
import net.createmod.catnip.gui.ScreenOpener
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.fml.DistExecutor

class RecordPressBaseBlock(
    properties: Properties,
) : Block(properties),
    IBE<RecordPressBaseBlockEntity>,
    IWrenchable,
    ProperWaterloggedBlock {
    companion object {
        private val RANDOM = RandomSource.create()
    }

    init {
        registerDefaultState(
            defaultBlockState().setValue(WATERLOGGED, false),
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block?, BlockState?>) {
        super.createBlockStateDefinition(builder.add(WATERLOGGED))
    }

    override fun getBlockEntityClass(): Class<RecordPressBaseBlockEntity> = RecordPressBaseBlockEntity::class.java

    override fun getBlockEntityType(): BlockEntityType<out RecordPressBaseBlockEntity> = ModBlockEntities.RECORD_PRESS_BASE.get()

    @Deprecated("Deprecated in Java")
    override fun getShape(
        pState: BlockState,
        pLevel: BlockGetter,
        pPos: BlockPos,
        pContext: CollisionContext,
    ): VoxelShape = AllShapes.CASING_13PX.get(Direction.UP)

    @Deprecated("Deprecated in Java")
    override fun use(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pPlayer: Player,
        pHand: InteractionHand,
        pHit: BlockHitResult,
    ): InteractionResult {
        if (AllItems.WRENCH.isIn(pPlayer.getItemInHand(pHand))) return InteractionResult.PASS

        // If clicked on any face except the top face, open the GUI
        if (pHit.direction != Direction.UP) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT) {
                Runnable {
                    withBlockEntityDo(pLevel, pPos) { be: RecordPressBaseBlockEntity ->
                        this.displayScreen(be, pPlayer)
                    }
                }
            }
            return InteractionResult.SUCCESS
        }

        if (pLevel.isClientSide) return InteractionResult.SUCCESS

        val behaviour =
            BlockEntityBehaviour.get(pLevel, pPos, RecordPressBaseBehaviour.BEHAVIOUR_TYPE)
                ?: return InteractionResult.PASS
        if (!behaviour.canAcceptItems()) return InteractionResult.SUCCESS

        val heldItem: ItemStack = pPlayer.getItemInHand(pHand)
        val wasEmptyHanded = heldItem.isEmpty
        val cantPlaceItem = AllBlocks.MECHANICAL_ARM.isIn(heldItem)

        val mainItemStack = behaviour.heldItemStack
        if (!mainItemStack.isEmpty) {
            pPlayer.inventory
                .placeItemBackInInventory(mainItemStack)
            behaviour.removeHeldItem()
            pLevel.playSound(
                null,
                pPos,
                SoundEvents.ITEM_PICKUP,
                SoundSource.PLAYERS,
                .2f,
                1f + RANDOM.nextFloat(),
            )
        }
        val outputs = behaviour.processingOutputBuffer
        for (i in 0..<outputs.slots) {
            pPlayer.inventory
                .placeItemBackInInventory(outputs.extractItem(i, 64, false))
        }

        if (!wasEmptyHanded && !cantPlaceItem) {
            val transported = TransportedItemStack(heldItem)
            transported.insertedFrom = pPlayer.direction
            transported.prevBeltPosition = .25f
            transported.beltPosition = .25f
            behaviour.heldItem = transported
            pPlayer.setItemInHand(pHand, ItemStack.EMPTY)
            AllSoundEvents.DEPOT_SLIDE.playOnServer(pLevel, pPos)
        }

        behaviour.blockEntity.notifyUpdate()
        return InteractionResult.SUCCESS
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState = withWater(super.getStateForPlacement(context), context)

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

    @Deprecated("Deprecated in Java")
    override fun onRemove(
        state: BlockState,
        worldIn: Level,
        pos: BlockPos,
        newState: BlockState,
        isMoving: Boolean,
    ) {
        IBE.onRemove(state, worldIn, pos, newState)
    }

    @Deprecated("Deprecated in Java")
    override fun getFluidState(pState: BlockState): FluidState = fluidState(pState)

    @OnlyIn(value = Dist.CLIENT)
    fun displayScreen(
        be: RecordPressBaseBlockEntity,
        player: Player,
    ) {
        if (player is LocalPlayer) ScreenOpener.open(RecordPressBaseScreen(be))
    }

    override fun updateEntityAfterFallOn(
        pLevel: BlockGetter,
        pEntity: Entity,
    ) {
        super.updateEntityAfterFallOn(pLevel, pEntity)
        SharedDepotBlockMethods.onLanded(pLevel, pEntity)
    }
}
