package me.mochibit.createharmonics.registry

import com.simibubi.create.api.contraption.storage.item.MountedItemStorageType
import com.tterrag.registrate.util.entry.RegistryEntry
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.cRegistrate
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlockEntity
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerMountedStorage
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext

object ModMountedStorages : AutoRegistrable {
    val SIMPLE_RECORD_PLAYER_STORAGE: RegistryEntry<RecordPlayerMountedStorageType> =
        cRegistrate()
            .mountedItemStorage("simple_record_player_storage", ::RecordPlayerMountedStorageType)
            .register()

    override fun register(
        eventBus: IEventBus,
        context: FMLJavaModLoadingContext,
    ) {
        Logger.info("Registering mounted storages for ${CreateHarmonicsMod.MOD_ID}")
    }

    class RecordPlayerMountedStorageType : MountedItemStorageType<RecordPlayerMountedStorage>(RecordPlayerMountedStorage.CODEC) {
        override fun mount(
            level: Level?,
            state: BlockState?,
            pos: BlockPos?,
            be: BlockEntity?,
        ): RecordPlayerMountedStorage? {
            if (be is RecordPlayerBlockEntity) {
                return RecordPlayerMountedStorage.fromRecordPlayer(be)
            }
            return null
        }
    }
}
