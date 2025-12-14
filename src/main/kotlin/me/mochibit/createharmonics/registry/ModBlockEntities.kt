package me.mochibit.createharmonics.registry

import com.tterrag.registrate.util.entry.BlockEntityEntry
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.cRegistrate
import me.mochibit.createharmonics.content.block.recordBurner.RecordBurnerVisual
import me.mochibit.createharmonics.content.block.recordBurner.RecordPressBaseBlockEntity
import me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerVisual
import me.mochibit.createharmonics.content.block.recordPlayer.andesiteJukebox.AndesiteJukeboxBlockEntity
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext

object ModBlockEntities : AutoRegistrable {
    val ANDESITE_JUKEBOX: BlockEntityEntry<AndesiteJukeboxBlockEntity> =
        cRegistrate()
            .blockEntity("andesite_jukebox", ::AndesiteJukeboxBlockEntity)
            .visual({
                SimpleBlockEntityVisualizer.Factory { ctx, be, pt ->
                    RecordPlayerVisual(ctx, be, pt)
                }
            }, false)
            .validBlocks(ModBlocks.ANDESITE_JUKEBOX)
            .register()

    val RECORD_PRESS_BASE: BlockEntityEntry<RecordPressBaseBlockEntity> =
        cRegistrate()
            .blockEntity("record_press_base", ::RecordPressBaseBlockEntity)
            .visual({
                SimpleBlockEntityVisualizer.Factory { ctx, be, pt ->
                    RecordBurnerVisual(ctx, be, pt)
                }
            }, false)
            .validBlocks(ModBlocks.RECORD_PRESS_BASE)
            .register()

    override fun register(
        eventBus: IEventBus,
        context: FMLJavaModLoadingContext,
    ) {
        info("Registering block entities for ${CreateHarmonicsMod.MOD_ID}")
    }
}
