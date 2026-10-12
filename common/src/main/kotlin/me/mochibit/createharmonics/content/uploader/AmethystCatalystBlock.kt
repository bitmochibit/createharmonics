package me.mochibit.createharmonics.content.uploader

import com.simibubi.create.AllItems
import com.simibubi.create.AllShapes
import com.simibubi.create.content.equipment.wrench.IWrenchable
import com.simibubi.create.foundation.block.IBE
import com.simibubi.create.foundation.block.ProperWaterloggedBlock
import com.simibubi.create.foundation.block.ProperWaterloggedBlock.WATERLOGGED
import me.mochibit.createharmonics.foundation.extension.onClient
import me.mochibit.createharmonics.foundation.extension.onServer
import me.mochibit.createharmonics.foundation.registry.ModBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

class AmethystCatalystBlock(
    val props: Properties
) : Block(props), IBE<AmethystCatalystBlockEntity>, IWrenchable, ProperWaterloggedBlock {
    companion object {
        val POWERED = BlockStateProperties.POWERED
    }

    init {
        registerDefaultState(
            defaultBlockState()
                .setValue(WATERLOGGED, false)
                .setValue(POWERED, false)
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder.add(POWERED, WATERLOGGED))
    }

    override fun getBlockEntityClass(): Class<AmethystCatalystBlockEntity> = AmethystCatalystBlockEntity::class.java

    override fun getBlockEntityType(): BlockEntityType<out AmethystCatalystBlockEntity> =
        ModBlockEntities.AMETHYST_CATALYST.get()

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return AllShapes.CASING_11PX.get(Direction.UP)
    }

    override fun useItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hitResult: BlockHitResult
    ): ItemInteractionResult = use(state, level, pos, player, hitResult, hand, stack)

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult = use(state, level, pos, player, hitResult).result()


    private fun use(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult,
        hand: InteractionHand? = null,
        clickedWithStack: ItemStack? = null
    ): ItemInteractionResult {
        val held = clickedWithStack ?: player.mainHandItem
        if (AllItems.WRENCH.isIn(held)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION

        if (hitResult.direction != Direction.UP) {
            level.onClient { _, _ -> withBlockEntityDo(level, pos) { ClientHandler.openAmethystCatalystScreen(it) } }
            return ItemInteractionResult.SUCCESS
        }

        val be = level.getBlockEntity(pos) as? AmethystCatalystBlockEntity
            ?: return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION

        val insert = held.`is`(Items.AMETHYST_SHARD) && !be.hasCrystal
        val extract = held.isEmpty && be.hasCrystal
        if (!insert && !extract) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION

        level.onServer {
            when {
                insert -> {
                    be.insertCrystal(held)
                    if (!player.hasInfiniteMaterials()) held.shrink(1)
                }
                be.isBusy -> be.shock(player)
                else -> player.inventory.placeItemBackInInventory(be.popCrystal())
            }
        }
        return ItemInteractionResult.SUCCESS
    }

    override fun onRemove(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        newState: BlockState,
        movedByPiston: Boolean
    ) {
        IBE.onRemove(state, level, pos, newState)
    }
}