package me.mochibit.createharmonics.content.block.recordPlayer.andesiteJukebox

import com.simibubi.create.AllShapes
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock
import com.simibubi.create.foundation.block.IBE
import me.mochibit.createharmonics.content.block.recordPlayer.PlaybackState
import me.mochibit.createharmonics.content.item.EtherealDiscItem
import me.mochibit.createharmonics.extension.onServer
import me.mochibit.createharmonics.registry.ModBlockEntitiesRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

class AndesiteJukeboxBlock(properties: Properties) : DirectionalKineticBlock(properties),
    IBE<AndesiteJukeboxBlockEntity> {

    // TODO Abstract RecordPlayerBlock

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
        val blockEntity = pLevel.getBlockEntity(pPos) as? AndesiteJukeboxBlockEntity ?: return InteractionResult.PASS
        val clickItem = pPlayer.getItemInHand(pHand)

        // |-> SNEAK TO REMOVE RECORD
        if (pPlayer.isShiftKeyDown) {
            if (!blockEntity.hasDisc()) {
                return InteractionResult.PASS
            }

            blockEntity.stopPlayer()

            pLevel.onServer {
                val disc = blockEntity.popDisc() ?: return@onServer
                pPlayer.addItem(disc)
                blockEntity.notifyUpdate()
            }
            return InteractionResult.SUCCESS
        }


        // |-> INSERT RECORD
        if (clickItem.item is EtherealDiscItem && !blockEntity.hasDisc()) {
            pLevel.onServer {
                blockEntity.insertDisc(clickItem)
                clickItem.shrink(1)
                blockEntity.notifyUpdate()
            }
            return InteractionResult.SUCCESS
        }

        // |-> BEHAVIOURS

        when (blockEntity.playbackState) {
            PlaybackState.PAUSED -> {
                // Don't do nu cazz
            }

            PlaybackState.PLAYING -> {
                blockEntity.stopPlayer()
            }

            PlaybackState.STOPPED -> {
                blockEntity.startPlayer()
            }
        }

        return InteractionResult.SUCCESS
    }

    override fun onRemove(
        pState: BlockState,
        pLevel: Level,
        pPos: BlockPos,
        pNewState: BlockState,
        pIsMoving: Boolean
    ) {
        super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving)
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