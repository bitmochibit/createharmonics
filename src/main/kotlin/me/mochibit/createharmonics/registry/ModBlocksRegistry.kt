package me.mochibit.createharmonics.registry

import com.simibubi.create.AllTags
import com.simibubi.create.foundation.data.ModelGen.customItemModel
import com.tterrag.registrate.util.entry.BlockEntry
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.cRegistrate
import me.mochibit.createharmonics.content.block.recordPlayer.andesiteJukebox.AndesiteJukeboxBlock
import net.minecraft.world.level.block.SoundType
import net.minecraftforge.eventbus.api.IEventBus

object ModBlocksRegistry : AbstractModRegistry {

    val ANDESITE_JUKEBOX: BlockEntry<AndesiteJukeboxBlock> = cRegistrate()
        .block("andesite_jukebox") { properties ->
            AndesiteJukeboxBlock(properties)
        }
        .properties { p ->
            p.strength(2.0f, 6.0f)
                .sound(SoundType.WOOD)
        }
        .tag(AllTags.AllBlockTags.SAFE_NBT.tag)
        .item()
        .transform(customItemModel())
        .register()

    override fun register(eventBus: IEventBus) {
        info("Registering blocks for ${CreateHarmonicsMod.MOD_ID}")
    }
}
