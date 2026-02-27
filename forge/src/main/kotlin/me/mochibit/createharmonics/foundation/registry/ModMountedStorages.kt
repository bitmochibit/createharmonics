package me.mochibit.createharmonics.foundation.registry

import com.simibubi.create.api.contraption.storage.item.MountedItemStorageType
import com.tterrag.registrate.util.entry.RegistryEntry
import me.mochibit.createharmonics.cRegistrate
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlockEntity
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerMountedStorage
import me.mochibit.createharmonics.foundation.info
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

object ModMountedStorages : Registrable, ForgeRegistry {
    override val registrationOrder = 1

    val SIMPLE_RECORD_PLAYER_STORAGE: RegistryEntry<RecordPlayerMountedStorageType> =
        cRegistrate()
            .mountedItemStorage("simple_record_player_storage", ::RecordPlayerMountedStorageType)
            .register()

    override fun register() {
        "Registering mounted storages".info()
    }

    class RecordPlayerMountedStorageType : MountedItemStorageType<RecordPlayerMountedStorage>(RecordPlayerMountedStorage.CODEC) {
        override fun mount(
            level: Level?,
            state: BlockState?,
            pos: BlockPos?,
            be: BlockEntity?,
        ): RecordPlayerMountedStorage? {
            if (be is RecordPlayerBlockEntity) {
                return RecordPlayerMountedStorage.Companion.fromRecordPlayer(be)
            }
            return null
        }
    }
}
