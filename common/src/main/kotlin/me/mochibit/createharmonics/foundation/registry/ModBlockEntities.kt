package me.mochibit.createharmonics.foundation.registry

import com.tterrag.registrate.util.entry.BlockEntityEntry
import com.tterrag.registrate.util.nullness.NonNullFunction
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer
import me.mochibit.createharmonics.ModRegistrate
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerRenderer
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerVisual
import me.mochibit.createharmonics.content.kinetics.recordPlayer.andesiteJukebox.AndesiteJukeboxBlockEntity
import me.mochibit.createharmonics.content.kinetics.recordPlayer.brassJukebox.BrassJukeboxBlockEntity
import me.mochibit.createharmonics.content.processing.recordPressBase.RecordPressBaseBlockEntity
import me.mochibit.createharmonics.content.processing.recordPressBase.RecordPressBaseRenderer
import me.mochibit.createharmonics.foundation.info

object ModBlockEntities : CommonRegistry {
    override val registrationOrder = 3

    val ANDESITE_JUKEBOX: BlockEntityEntry<AndesiteJukeboxBlockEntity> =
        ModRegistrate
            .blockEntity("andesite_jukebox", ::AndesiteJukeboxBlockEntity)
            .visual({
                SimpleBlockEntityVisualizer.Factory { ctx, be, pt ->
                    RecordPlayerVisual(ctx, be, pt)
                }
            }, false)
            .validBlocks(ModBlocks.ANDESITE_JUKEBOX)
            .renderer {
                NonNullFunction { ctx ->
                    RecordPlayerRenderer(ctx)
                }
            }.register()

    val BRASS_JUKEBOX: BlockEntityEntry<BrassJukeboxBlockEntity> =
        ModRegistrate
            .blockEntity("brass_jukebox", ::BrassJukeboxBlockEntity)
            .visual({
                SimpleBlockEntityVisualizer.Factory { ctx, be, pt ->
                    RecordPlayerVisual(ctx, be, pt)
                }
            }, false)
            .validBlocks(ModBlocks.BRASS_JUKEBOX)
            .renderer {
                NonNullFunction { ctx ->
                    RecordPlayerRenderer(ctx)
                }
            }.register()

    val RECORD_PRESS_BASE: BlockEntityEntry<RecordPressBaseBlockEntity> =
        ModRegistrate
            .blockEntity("record_press_base", ::RecordPressBaseBlockEntity)
            .validBlocks(ModBlocks.RECORD_PRESS_BASE)
            .renderer {
                NonNullFunction { ctx ->
                    RecordPressBaseRenderer(ctx)
                }
            }.register()

    override fun register() {
        "Registering block entities".info()
    }
}
