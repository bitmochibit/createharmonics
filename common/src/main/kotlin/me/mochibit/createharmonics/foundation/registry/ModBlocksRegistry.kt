package me.mochibit.createharmonics.foundation.registry

import net.minecraft.world.level.block.Block

interface ModBlocksRegistry<RegistryObjectType> : CrossPlatformRegistry<RegistryObjectType, Block> {
    val andesiteJukebox: Block
    val brassJukebox: Block
    val recordPressBase: Block
}
