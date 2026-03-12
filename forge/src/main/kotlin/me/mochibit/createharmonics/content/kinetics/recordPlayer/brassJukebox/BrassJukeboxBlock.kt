package me.mochibit.createharmonics.content.kinetics.recordPlayer.brassJukebox

import me.mochibit.createharmonics.content.kinetics.recordPlayer.HorizontalRecordPlayerBlock
import me.mochibit.createharmonics.foundation.registry.ModBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

class BrassJukeboxBlock(
    properties: Properties,
) : HorizontalRecordPlayerBlock(properties) {
    override fun getBlockEntityType(): BlockEntityType<out BrassJukeboxBlockEntity> = ModBlockEntities.BRASS_JUKEBOX.get()
}
