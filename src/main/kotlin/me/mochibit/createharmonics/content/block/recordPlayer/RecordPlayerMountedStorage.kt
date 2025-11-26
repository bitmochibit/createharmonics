package me.mochibit.createharmonics.content.block.recordPlayer

import com.mojang.serialization.Codec
import com.simibubi.create.api.contraption.storage.SyncedMountedStorage
import com.simibubi.create.api.contraption.storage.item.simple.SimpleMountedStorage
import com.simibubi.create.content.contraptions.Contraption
import me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerBlockEntity.Companion.RECORD_SLOT
import me.mochibit.createharmonics.content.item.EtherealRecordItem
import me.mochibit.createharmonics.registry.ModMountedStorageRegistry
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import net.minecraft.world.phys.Vec3
import net.minecraftforge.items.IItemHandler

class RecordPlayerMountedStorage(handler: IItemHandler) :
    SimpleMountedStorage(ModMountedStorageRegistry.SIMPLE_RECORD_PLAYER_STORAGE.get(), handler), SyncedMountedStorage {

    private var dirty = false
    override fun isItemValid(slot: Int, stack: ItemStack): Boolean {
        return stack.item is EtherealRecordItem || stack.isEmpty
    }

    override fun handleInteraction(
        player: ServerPlayer?,
        contraption: Contraption?,
        info: StructureTemplate.StructureBlockInfo?
    ): Boolean {
        return false
    }

    override fun playOpeningSound(level: ServerLevel?, pos: Vec3?) {}

    override fun isDirty(): Boolean {
        return dirty
    }

    override fun markClean() {
        dirty = false
    }

    fun markDirty() {
        dirty = true
    }

    override fun afterSync(
        contraption: Contraption,
        localPos: BlockPos
    ) {
        val be = contraption.presentBlockEntities[localPos]
        if (be is RecordPlayerBlockEntity) {
            be.inventoryHandler.setStackInSlot(RECORD_SLOT, this.getStackInSlot(RECORD_SLOT))
        }
    }

    companion object {
        val CODEC: Codec<RecordPlayerMountedStorage> =
            codec<RecordPlayerMountedStorage> { handler: IItemHandler ->
                RecordPlayerMountedStorage(handler)
            }
    }
}

