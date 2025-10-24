package me.mochibit.createharmonics.registry

import com.simibubi.create.foundation.data.CreateBlockEntityBuilder
import com.tterrag.registrate.util.entry.BlockEntityEntry
import dev.engine_room.flywheel.api.visual.BlockEntityVisual
import dev.engine_room.flywheel.api.visualization.VisualizationContext
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.cRegistrate
import me.mochibit.createharmonics.content.block.andesiteJukebox.AndesiteJukeboxBlock
import me.mochibit.createharmonics.content.block.andesiteJukebox.AndesiteJukeboxBlockEntity
import me.mochibit.createharmonics.content.block.andesiteJukebox.AndesiteJukeboxVisual
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraftforge.eventbus.api.IEventBus
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

object ModBlockEntitiesRegistry : AbstractModRegistry {

    val ANDESITE_JUKEBOX: BlockEntityEntry<AndesiteJukeboxBlockEntity> = cRegistrate()
        .blockEntity("andesite_jukebox", ::AndesiteJukeboxBlockEntity)
        .visual<AndesiteJukeboxBlockEntity, AndesiteJukeboxVisual>(false)
        .validBlocks(ModBlocksRegistry.ANDESITE_JUKEBOX)
        .register()

    override fun register(eventBus: IEventBus) {
        info("Registering block entities for ${CreateHarmonicsMod.MOD_ID}")
    }
}

inline fun <T: BlockEntity, reified visualizer: BlockEntityVisual<T>> CreateBlockEntityBuilder<T,*>.visual(
    renderNormally: Boolean,
): CreateBlockEntityBuilder<T, *> {
    return visual(
        {
            if (visualizer::class.primaryConstructor == null) {
                throw IllegalArgumentException("Visualizer class must have a primary constructor")
            }
            SimpleBlockEntityVisualizer.Factory { ctx, be, pt ->
                visualizer::class.primaryConstructor?.call(ctx, be, pt)
            }
        },
        renderNormally
    )
}



