package me.mochibit.createharmonics.foundation.services

import com.simibubi.create.api.contraption.storage.item.MountedItemStorageType
import com.tterrag.registrate.util.entry.RegistryEntry
import me.mochibit.createharmonics.foundation.inventory.GenericInventory
import me.mochibit.createharmonics.foundation.inventory.InventorySpec
import me.mochibit.createharmonics.foundation.inventory.StorageInventory
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.material.FluidState

/**
 * Content service
 *
 */
interface ContentService {
    /**
     * Returns the viscosity
     *
     * @param fluidState the fluid state
     * @return the int
     */// Helpers for platform-specific features
    fun getViscosity(fluidState: FluidState): Int

    /**
     * Invalidate block entity storage
     *
     * @param be
     */
    fun invalidateBlockEntityStorage(be: BlockEntity)

    /**
     * Creates a new inventory from spec
     *
     * @param spec
     * @param onChange the on change
     * @receiver
     * @param onLoad the on load
     * @receiver
     * @return the storage inventory
     */
    fun createInventoryFromSpec(spec: InventorySpec,
                                onChange: GenericInventory.(slot: Int) -> Unit = {},
                                onLoad: GenericInventory.() -> Unit = {}
                                ): StorageInventory

    /**
     * Simple record player mounted storage
     *
     * @return the registry entry
     */
    fun simpleRecordPlayerMountedStorage(): RegistryEntry<MountedItemStorageType<*>, *>
}

val contentService: ContentService by lazy {
    loadService<ContentService>()
}
