package me.mochibit.createharmonics.registry

import com.tterrag.registrate.util.entry.BlockEntityEntry
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.cRegistrate
import me.mochibit.createharmonics.content.block.andesiteJukebox.AndesiteJukeboxBlockEntity
import me.mochibit.createharmonics.content.block.andesiteJukebox.AndesiteJukeboxVisual
import net.minecraftforge.eventbus.api.IEventBus

object ModBlockEntitiesRegistry : AbstractModRegistry {

    val ANDESITE_JUKEBOX: BlockEntityEntry<AndesiteJukeboxBlockEntity> = cRegistrate()
        .blockEntity("andesite_jukebox", ::AndesiteJukeboxBlockEntity)
        .visual({
            SimpleBlockEntityVisualizer.Factory { ctx, be, pt ->
                AndesiteJukeboxVisual(ctx, be as AndesiteJukeboxBlockEntity, pt)
            }
        }, false)
        .validBlocks(ModBlocksRegistry.ANDESITE_JUKEBOX)
        .register()

    override fun register(eventBus: IEventBus) {
        info("Registering block entities for ${CreateHarmonicsMod.MOD_ID}")
    }
}

