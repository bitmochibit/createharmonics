package me.mochibit.createharmonics.foundation.registry.platform

import net.minecraft.world.level.block.Block

abstract class ModBlocksRegistry<RegistryObjectType> : CrossPlatformRegistry<RegistryObjectType, Block> {
    override val referenceMap: MutableMap<Block, RegistryObjectType> = mutableMapOf()

    abstract val andesiteJukebox: Block
    abstract val brassJukebox: Block
    abstract val recordPressBase: Block

    override fun registerEntry(name: String): CrossPlatformRegistry.ConvertibleEntry<RegistryObjectType, Block> =
        throw UnsupportedOperationException("You must use registrate for making entries of blocks!")
}
