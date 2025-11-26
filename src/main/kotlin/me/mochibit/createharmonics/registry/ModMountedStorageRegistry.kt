package me.mochibit.createharmonics.registry

import com.simibubi.create.api.contraption.storage.item.simple.SimpleMountedStorage
import com.simibubi.create.api.contraption.storage.item.simple.SimpleMountedStorageType
import com.tterrag.registrate.util.entry.RegistryEntry
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.cRegistrate
import me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerMountedStorage
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.items.IItemHandler


object ModMountedStorageRegistry : AbstractModRegistry {
    val SIMPLE_RECORD_PLAYER_STORAGE: RegistryEntry<RecordPlayerMountedStorageType> = cRegistrate()
        .mountedItemStorage("simple_record_player_storage") {
            RecordPlayerMountedStorageType()
        }.register()

    override fun register(eventBus: IEventBus, context: FMLJavaModLoadingContext) {
        Logger.info("Registering mounted storages for ${CreateHarmonicsMod.MOD_ID}")
    }


    class RecordPlayerMountedStorageType :
        SimpleMountedStorageType<RecordPlayerMountedStorage>(RecordPlayerMountedStorage.CODEC) {
        override fun createStorage(handler: IItemHandler): SimpleMountedStorage {
            return RecordPlayerMountedStorage(handler)
        }
    }
}