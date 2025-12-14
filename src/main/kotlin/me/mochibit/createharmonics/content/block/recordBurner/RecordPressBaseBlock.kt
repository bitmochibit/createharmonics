package me.mochibit.createharmonics.content.block.recordBurner

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock
import com.simibubi.create.foundation.block.IBE
import me.mochibit.createharmonics.registry.ModBlockEntities
import net.minecraft.core.Direction
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

class RecordPressBaseBlock(
    properties: Properties,
) : HorizontalKineticBlock(properties),
    IBE<RecordPressBaseBlockEntity> {
    override fun getRotationAxis(state: BlockState?): Direction.Axis = Direction.UP.axis

    override fun getBlockEntityClass(): Class<RecordPressBaseBlockEntity> = RecordPressBaseBlockEntity::class.java

    override fun getBlockEntityType(): BlockEntityType<out RecordPressBaseBlockEntity> = ModBlockEntities.RECORD_PRESS_BASE.get()
}
