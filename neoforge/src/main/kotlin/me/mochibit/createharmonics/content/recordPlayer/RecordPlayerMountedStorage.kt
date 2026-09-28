package me.mochibit.createharmonics.content.recordPlayer


import com.mojang.serialization.MapCodec
import com.simibubi.create.api.contraption.storage.SyncedMountedStorage
import com.simibubi.create.api.contraption.storage.item.MountedItemStorageType
import com.simibubi.create.api.contraption.storage.item.WrapperMountedItemStorage
import com.simibubi.create.content.contraptions.Contraption
import com.simibubi.create.foundation.codec.CreateCodecs
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBehaviour
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlockEntity
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerMountedStorageLogic
import me.mochibit.createharmonics.foundation.inventory.SpecItemHandler
import me.mochibit.createharmonics.foundation.registry.ModMountedStorages
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import net.minecraft.world.phys.Vec3

class RecordPlayerMountedStorage(
    type: MountedItemStorageType<*>,
    handler: SpecItemHandler,
) : WrapperMountedItemStorage<SpecItemHandler>(type, handler), SyncedMountedStorage {

    companion object {
        private val SPEC = RecordPlayerBehaviour.RecordPlayerInventory.SpecBasis

        @JvmStatic
        val CODEC: MapCodec<RecordPlayerMountedStorage> =
            CreateCodecs.ITEM_STACK_HANDLER.fieldOf("inventory").xmap(
                { src ->
                    val h = SpecItemHandler(SPEC)
                    for (i in 0 until src.slots) h.setStackInSlot(i, src.getStackInSlot(i).copy())
                    RecordPlayerMountedStorage(h)
                },
            ) { storage -> storage.wrapped }

        fun fromRecordPlayer(be: RecordPlayerBlockEntity) =
            RecordPlayerMountedStorage(SpecItemHandler.copyOf(be.itemHandler, SPEC))
    }

    constructor(wrapped: SpecItemHandler) :
            this(ModMountedStorages.SIMPLE_RECORD_PLAYER_STORAGE.get(), wrapped)

    private var dirty = false

    init {
        wrapped.onChangeListener = { dirty = true }
    }


    override fun unmount(level: Level?, state: BlockState?, pos: BlockPos?, be: BlockEntity?) {
        if (be is RecordPlayerBlockEntity) be.applyInventoryToBlock(wrapped)
    }

    override fun handleInteraction(
        player: ServerPlayer, contraption: Contraption, info: StructureTemplate.StructureBlockInfo,
    ) = RecordPlayerMountedStorageLogic.handleInteraction(wrapped, player)

    override fun playOpeningSound(level: ServerLevel?, pos: Vec3?) {}
    override fun isDirty() = dirty
    override fun markClean() {
        dirty = false
    }

    override fun afterSync(contraption: Contraption, localPos: BlockPos) {
        val be = contraption.getBlockEntityClientSide(localPos)
        if (be is RecordPlayerBlockEntity) {
            be.playerBehaviour.setRecord(wrapped.getStackInSlot(RecordPlayerBehaviour.RecordPlayerInventory.MAIN_RECORD_SLOT))
        }
    }
}
