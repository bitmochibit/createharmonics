package me.mochibit.createharmonics.registry

import com.tterrag.registrate.util.entry.BlockEntityEntry
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.cRegistrate
import me.mochibit.createharmonics.content.blockEntity.AndesiteJukeboxBlockEntity
import net.minecraftforge.eventbus.api.IEventBus

object ModBlockEntitiesRegistry : AbstractModRegistry {

    val ANDESITE_JUKEBOX: BlockEntityEntry<AndesiteJukeboxBlockEntity> = cRegistrate()
        .blockEntity("andesite_jukebox", ::AndesiteJukeboxBlockEntity)
        .validBlocks(ModBlocksRegistry.ANDESITE_JUKEBOX)
        .register()

    override fun register(eventBus: IEventBus) {
        info("Registering block entities for ${CreateHarmonicsMod.MOD_ID}")
    }
}

