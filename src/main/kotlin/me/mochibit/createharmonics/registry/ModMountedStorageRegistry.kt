package me.mochibit.createharmonics.registry

import com.tterrag.registrate.util.entry.RegistryEntry
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.cRegistrate
import me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerMountedStorageType
import net.minecraftforge.eventbus.api.IEventBus

object ModMountedStorageRegistry : AbstractModRegistry {
    val SIMPLE_RECORD_PLAYER_STORAGE: RegistryEntry<RecordPlayerMountedStorageType> = cRegistrate()
        .mountedItemStorage("simple_record_player_storage") {
            RecordPlayerMountedStorageType()
        }.register()

    override fun register(eventBus: IEventBus) {
        Logger.info("Registering mounted storages for ${CreateHarmonicsMod.MOD_ID}")
    }

}