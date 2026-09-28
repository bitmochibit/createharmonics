package me.mochibit.createharmonics.foundation.services

import com.simibubi.create.api.contraption.storage.item.MountedItemStorageType
import com.tterrag.registrate.util.entry.RegistryEntry
import me.mochibit.createharmonics.foundation.inventory.GenericInventory
import me.mochibit.createharmonics.foundation.inventory.InventorySpec
import me.mochibit.createharmonics.foundation.inventory.StorageInventory
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.material.FluidState

interface ContentService {
    // Helpers for platform-specific features
    fun getViscosity(fluidState: FluidState): Int

    fun invalidateBlockEntityStorage(be: BlockEntity)

    fun createInventoryFromSpec(spec: InventorySpec,
                                onChange: GenericInventory.(slot: Int) -> Unit = {},
                                onLoad: GenericInventory.() -> Unit = {}
                                ): StorageInventory

    fun simpleRecordPlayerMountedStorage(): RegistryEntry<MountedItemStorageType<*>, *>
}

val contentService: ContentService by lazy {
    loadService<ContentService>()
}
