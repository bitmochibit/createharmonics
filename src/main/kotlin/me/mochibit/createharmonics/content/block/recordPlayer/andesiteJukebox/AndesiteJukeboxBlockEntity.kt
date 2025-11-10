package me.mochibit.createharmonics.content.block.recordPlayer.andesiteJukebox

import me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

class AndesiteJukeboxBlockEntity(
    type: BlockEntityType<AndesiteJukeboxBlockEntity>,
    pos: BlockPos,
    state: BlockState
) : RecordPlayerBlockEntity(type, pos, state), MenuProvider {

    override fun createMenu(id: Int, playerInventory: Inventory, player: Player): AbstractContainerMenu {
        return AndesiteJukeboxMenu(id, playerInventory, this)
    }

    override fun getDisplayName(): Component {
        return Component.translatable("block.createharmonics.andesite_jukebox")
    }
}