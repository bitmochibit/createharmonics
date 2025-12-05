package me.mochibit.createharmonics.content.block.recordPlayer

import com.mojang.serialization.Codec
import com.simibubi.create.api.contraption.storage.SyncedMountedStorage
import com.simibubi.create.api.contraption.storage.item.MountedItemStorageType
import com.simibubi.create.api.contraption.storage.item.WrapperMountedItemStorage
import com.simibubi.create.content.contraptions.Contraption
import com.simibubi.create.foundation.utility.CreateCodecs
import me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerItemHandler.Companion.MAIN_RECORD_SLOT
import me.mochibit.createharmonics.content.item.EtherealRecordItem
import me.mochibit.createharmonics.registry.ModMountedStorageRegistry
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import net.minecraft.world.phys.Vec3
import net.minecraftforge.items.ItemStackHandler

class RecordPlayerMountedStorage(
    mountedStorageType: MountedItemStorageType<*>,
    handler: ItemStackHandler,
) : WrapperMountedItemStorage<ItemStackHandler>(
        mountedStorageType,
        handler,
    ),
    SyncedMountedStorage {
    constructor(wrapped: ItemStackHandler) : this(ModMountedStorageRegistry.SIMPLE_RECORD_PLAYER_STORAGE.get(), wrapped)

    private var dirty = true // Need immediate resync

    override fun isItemValid(
        slot: Int,
        stack: ItemStack,
    ): Boolean = stack.item is EtherealRecordItem || stack.isEmpty

    override fun unmount(
        level: Level?,
        state: BlockState?,
        pos: BlockPos?,
        be: BlockEntity?,
    ) {
        if (be is RecordPlayerBlockEntity) {
            be.applyInventoryToBlock(this.wrapped)
        }
    }

    override fun handleInteraction(
        player: ServerPlayer,
        contraption: Contraption,
        info: StructureTemplate.StructureBlockInfo,
    ): Boolean {
        val itemInHand = player.mainHandItem

        if (player.isShiftKeyDown) {
            val discItem = getRecord().copy()
            if (discItem.isEmpty) return false
            setRecord(ItemStack.EMPTY)
            dirty = true
            if (!player.inventory.add(discItem)) {
                player.drop(discItem, false)
            }
        } else {
            val discItem = getRecord().copy()
            if (!discItem.isEmpty) return false
            if (itemInHand.item is EtherealRecordItem) {
                setRecord(itemInHand.copy())
                dirty = true
                itemInHand.shrink(1)
            }
        }
        return true
    }

    override fun playOpeningSound(
        level: ServerLevel?,
        pos: Vec3?,
    ) {
    }

    override fun isDirty(): Boolean = dirty

    override fun markClean() {
        dirty = false
    }

    override fun afterSync(
        contraption: Contraption,
        localPos: BlockPos,
    ) {
    }

    fun setRecord(recordStack: ItemStack) {
        this.wrapped.setStackInSlot(MAIN_RECORD_SLOT, recordStack)
    }

    fun getRecord(): ItemStack = this.wrapped.getStackInSlot(MAIN_RECORD_SLOT)

    companion object {
        val CODEC: Codec<RecordPlayerMountedStorage> =
            CreateCodecs.ITEM_STACK_HANDLER
                .xmap(
                    ::RecordPlayerMountedStorage,
                    { storage -> storage.wrapped },
                )

        fun fromRecordPlayer(be: RecordPlayerBlockEntity): RecordPlayerMountedStorage {
            val handler = be.lazyItemHandler.resolve().get()
            return RecordPlayerMountedStorage(copyToItemStackHandler(handler))
        }
    }
}
