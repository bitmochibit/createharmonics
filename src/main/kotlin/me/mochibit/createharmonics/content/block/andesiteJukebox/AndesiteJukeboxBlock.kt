package me.mochibit.createharmonics.content.block.andesiteJukebox

import com.simibubi.create.AllShapes
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock
import com.simibubi.create.foundation.block.IBE
import me.mochibit.createharmonics.registry.ModBlockEntitiesRegistry
import me.mochibit.createharmonics.registry.ModItemsRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
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
import net.minecraft.world.phys.shapes.EntityCollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

class AndesiteJukeboxBlock(properties: Properties) : DirectionalKineticBlock(properties),
    IBE<AndesiteJukeboxBlockEntity> {


    @Deprecated("Deprecated in Java")
    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return AllShapes.CASING_14PX[Direction.DOWN]
    }

    @Deprecated("Deprecated in Java")
    override fun use(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pPlayer: Player,
        pHand: InteractionHand,
        pHit: BlockHitResult
    ): InteractionResult {
//        if (pLevel.isClientSide) return InteractionResult.SUCCESS

        val blockEntity = pLevel.getBlockEntity(pPos) as? AndesiteJukeboxBlockEntity ?: return InteractionResult.PASS
        blockEntity.startPlaying()
//        // If player is holding an ethereal disc, try to insert it directly
//        val heldItem = pPlayer.mainHandItem
//        if (heldItem.item == ModItemsRegistry.etherealDisc.get()) {
//            val slotStack = blockEntity.inventory.getStackInSlot(0)
//            if (slotStack.isEmpty) {
//                // Insert the disc
//                blockEntity.inventory.setStackInSlot(0, heldItem.copy().apply { count = 1 })
//                if (!pPlayer.abilities.instabuild) {
//                    heldItem.shrink(1)
//                }
//                return InteractionResult.CONSUME
//            }
//        }
//
//        // Otherwise, open the GUI
//        if (pPlayer is ServerPlayer) {
//            pPlayer.openMenu(blockEntity)
//        }
        return InteractionResult.CONSUME
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {
        if (!state.`is`(newState.block)) {
            val blockEntity = level.getBlockEntity(pos) as? AndesiteJukeboxBlockEntity
            blockEntity?.stopPlaying()
            // Drop inventory contents
            blockEntity?.let {
                val stack = it.inventory.getStackInSlot(0)
                if (!stack.isEmpty) {
                    net.minecraft.world.Containers.dropItemStack(level, pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), stack)
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving)
    }

    override fun hasShaftTowards(world: LevelReader?, pos: BlockPos?, state: BlockState, face: Direction?): Boolean {
        return face == state.getValue(FACING)
    }

    override fun getRotationAxis(state: BlockState): Direction.Axis {
        return state.getValue(FACING).axis
    }

    override fun getBlockEntityClass(): Class<AndesiteJukeboxBlockEntity> {
        return AndesiteJukeboxBlockEntity::class.java
    }

    override fun getBlockEntityType(): BlockEntityType<out AndesiteJukeboxBlockEntity> {
        return ModBlockEntitiesRegistry.ANDESITE_JUKEBOX.get()
    }
}