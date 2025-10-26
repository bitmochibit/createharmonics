package me.mochibit.createharmonics.content.block.recordPlayer.andesiteJukebox

import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.audio.AudioPlayer
import me.mochibit.createharmonics.audio.StreamRegistry
import me.mochibit.createharmonics.audio.effect.*
import me.mochibit.createharmonics.audio.instance.StaticSoundInstance
import me.mochibit.createharmonics.audio.pcm.PitchFunction
import me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerBlockEntity
import me.mochibit.createharmonics.coroutine.launchModCoroutine
import me.mochibit.createharmonics.coroutine.withClientContext
import me.mochibit.createharmonics.registry.ModItemsRegistry
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.items.IItemHandler
import net.minecraftforge.items.ItemStackHandler
import kotlin.math.abs

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