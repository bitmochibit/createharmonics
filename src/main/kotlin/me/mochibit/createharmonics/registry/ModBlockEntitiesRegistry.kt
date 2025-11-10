package me.mochibit.createharmonics.registry

import com.simibubi.create.foundation.data.CreateBlockEntityBuilder
import com.tterrag.registrate.util.entry.BlockEntityEntry
import dev.engine_room.flywheel.api.visual.BlockEntityVisual
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.cRegistrate
import me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerBlockEntity
import me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerVisual
import me.mochibit.createharmonics.content.block.recordPlayer.andesiteJukebox.AndesiteJukeboxBlockEntity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraftforge.common.util.NonNullSupplier
import net.minecraftforge.eventbus.api.IEventBus
import kotlin.reflect.full.primaryConstructor

object ModBlockEntitiesRegistry : AbstractModRegistry {

    val ANDESITE_JUKEBOX: BlockEntityEntry<AndesiteJukeboxBlockEntity> = cRegistrate()
        .blockEntity("andesite_jukebox", ::AndesiteJukeboxBlockEntity)
        .visual({
            SimpleBlockEntityVisualizer.Factory { ctx, be, pt ->
                RecordPlayerVisual(ctx, be, pt)
            }
        }, false)
        .validBlocks(ModBlocksRegistry.ANDESITE_JUKEBOX)
        .register()

    override fun register(eventBus: IEventBus) {
        info("Registering block entities for ${CreateHarmonicsMod.MOD_ID}")
    }
}



