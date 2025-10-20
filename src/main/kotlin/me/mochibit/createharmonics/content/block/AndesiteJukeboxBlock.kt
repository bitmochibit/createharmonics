package me.mochibit.createharmonics.content.block

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock
import com.simibubi.create.foundation.block.IBE
import me.mochibit.createharmonics.content.blockEntity.AndesiteJukeboxBlockEntity
import me.mochibit.createharmonics.content.item.EtherealDiscItem
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

class AndesiteJukeboxBlock(properties: Properties) : DirectionalKineticBlock(properties), IBE<AndesiteJukeboxBlockEntity> {

    companion object {
        val HAS_DISC: BooleanProperty = BooleanProperty.create("has_disc")
        private val SHAPE: VoxelShape = box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0)
    }

    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(HAS_DISC, false)
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(HAS_DISC)
        super.createBlockStateDefinition(builder)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        return defaultBlockState().setValue(FACING, Direction.UP)
    }

    @Deprecated("Deprecated in Java")
    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return SHAPE
    }

    @Deprecated("Deprecated in Java")
    override fun use(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult
    ): InteractionResult {
        if (level.isClientSide) return InteractionResult.SUCCESS

        val itemInHand = player.getItemInHand(hand)
        val blockEntity = level.getBlockEntity(pos) as? AndesiteJukeboxBlockEntity ?: return InteractionResult.PASS

        // Check if player is holding an EtherealDiscItem
        if (itemInHand.item is EtherealDiscItem) {
            if (!state.getValue(HAS_DISC)) {
                // Insert disc
                blockEntity.insertDisc()
                level.setBlock(pos, state.setValue(HAS_DISC, true), 3)
                if (!player.abilities.instabuild) {
                    itemInHand.shrink(1)
                }
                return InteractionResult.CONSUME
            }
        } else if (hand == InteractionHand.MAIN_HAND && itemInHand.isEmpty) {
            // Remove disc with empty hand
            if (state.getValue(HAS_DISC)) {
                blockEntity.ejectDisc()
                level.setBlock(pos, state.setValue(HAS_DISC, false), 3)
                // Optionally give disc back to player
                player.addItem(net.minecraft.world.item.ItemStack(
                    me.mochibit.createharmonics.registry.ModItemsRegistry.etherealDisc.get()
                ))
                return InteractionResult.CONSUME
            }
        }

        return InteractionResult.PASS
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {
        if (!state.`is`(newState.block)) {
            val blockEntity = level.getBlockEntity(pos) as? AndesiteJukeboxBlockEntity
            blockEntity?.stopPlaying()
        }
        super.onRemove(state, level, pos, newState, isMoving)
    }

    override fun getRotationAxis(state: BlockState): Direction.Axis {
        return Direction.Axis.Y
    }

    override fun hasShaftTowards(world: LevelReader, pos: BlockPos, state: BlockState, face: Direction): Boolean {
        return face == Direction.DOWN
    }

    override fun getBlockEntityClass(): Class<AndesiteJukeboxBlockEntity> {
        return AndesiteJukeboxBlockEntity::class.java
    }

    override fun getBlockEntityType(): BlockEntityType<out AndesiteJukeboxBlockEntity> {
        return me.mochibit.createharmonics.registry.ModBlockEntitiesRegistry.ANDESITE_JUKEBOX.get()
    }
}
