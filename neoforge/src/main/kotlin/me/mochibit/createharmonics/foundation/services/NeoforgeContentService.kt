package me.mochibit.createharmonics.foundation.services

import com.simibubi.create.api.contraption.storage.item.MountedItemStorageType
import com.tterrag.registrate.util.entry.RegistryEntry
import me.mochibit.createharmonics.foundation.inventory.GenericInventory
import me.mochibit.createharmonics.foundation.inventory.InventorySpec
import me.mochibit.createharmonics.foundation.inventory.StorageInventory
import me.mochibit.createharmonics.foundation.inventory.SpecItemHandler
import me.mochibit.createharmonics.foundation.registry.ModMountedStorages
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.material.FluidState

class NeoforgeContentService : ContentService {
    override fun getViscosity(fluidState: FluidState): Int = fluidState.fluidType.viscosity

    override fun invalidateBlockEntityStorage(be: BlockEntity) {
        be.invalidateCapabilities()
    }

    override fun createInventoryFromSpec(
        spec: InventorySpec,
        onChange: GenericInventory.(slot: Int) -> Unit,
        onLoad: GenericInventory.() -> Unit
    ): StorageInventory = SpecItemHandler(spec, onChange, onLoad)

    override fun simpleRecordPlayerMountedStorage(): RegistryEntry<MountedItemStorageType<*>, *> =
        ModMountedStorages.SIMPLE_RECORD_PLAYER_STORAGE

}

