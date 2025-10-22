package me.mochibit.createharmonics.content.block.andesiteJukebox

import com.simibubi.create.AllShapes
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock
import com.simibubi.create.foundation.block.IBE
import me.mochibit.createharmonics.registry.ModBlockEntitiesRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
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
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.EntityCollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

class AndesiteJukeboxBlock(properties: Properties) : DirectionalKineticBlock(properties),
    IBE<AndesiteJukeboxBlockEntity> {

    companion object {
        val HAS_DISC: BooleanProperty = BooleanProperty.create("has_disc")
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

    @Deprecated("Deprecated in Java")
    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return AllShapes.CASING_14PX[Direction.DOWN]
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val preferred = getPreferredFacing(context)
        if ((context.player != null && context.player!!
                .isShiftKeyDown) || preferred == null
        ) return super.getStateForPlacement(context)
        return defaultBlockState().setValue(FACING, preferred)
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {
        if (!state.`is`(newState.block)) {
            val blockEntity = level.getBlockEntity(pos) as? AndesiteJukeboxBlockEntity
            blockEntity?.stopPlaying()
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