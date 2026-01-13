package me.mochibit.createharmonics.registry

import com.simibubi.create.AllTags
import com.simibubi.create.api.behaviour.display.DisplaySource.displaySource
import com.simibubi.create.api.behaviour.movement.MovementBehaviour.movementBehaviour
import com.simibubi.create.api.contraption.storage.item.MountedItemStorageType.mountedItemStorage
import com.simibubi.create.foundation.data.AssetLookup
import com.simibubi.create.foundation.data.BlockStateGen
import com.simibubi.create.foundation.data.ModelGen.customItemModel
import com.tterrag.registrate.util.entry.BlockEntry
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.cRegistrate
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerMovementBehaviour
import me.mochibit.createharmonics.content.kinetics.recordPlayer.andesiteJukebox.AndesiteJukeboxBlock
import me.mochibit.createharmonics.content.processing.recordPressBase.RecordPressBaseBlock
import net.minecraft.world.level.block.SoundType
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext

object ModBlocks : AutoRegistrable {
    override val registrationOrder = 2

    val ANDESITE_JUKEBOX: BlockEntry<AndesiteJukeboxBlock> =
        cRegistrate()
            .block("andesite_jukebox") { properties ->
                AndesiteJukeboxBlock(properties)
            }.properties { p ->
                p
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.WOOD)
            }.blockstate(BlockStateGen.directionalBlockProvider(true))
            .onRegister(movementBehaviour(RecordPlayerMovementBehaviour()))
            .tag(AllTags.AllBlockTags.SAFE_NBT.tag)
            .tag(AllTags.AllBlockTags.SIMPLE_MOUNTED_STORAGE.tag)
            .transform(mountedItemStorage(ModMountedStorages.SIMPLE_RECORD_PLAYER_STORAGE))
            .transform(displaySource(ModDisplaySources.AUDIO_NAME))
            .item()
            .tag(AllTags.AllItemTags.CONTRAPTION_CONTROLLED.tag)
            .transform(customItemModel())
            .register()

    val RECORD_PRESS_BASE: BlockEntry<RecordPressBaseBlock> =
        cRegistrate()
            .block("record_press_base", ::RecordPressBaseBlock)
            .properties { p ->
                p
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.COPPER)
            }.tag(AllTags.AllBlockTags.SAFE_NBT.tag)
            .blockstate { ctx, p ->
                p.simpleBlock(ctx.entry, AssetLookup.partialBaseModel(ctx, p))
            }.item()
            .transform(customItemModel())
            .register()

    override fun register(
        eventBus: IEventBus,
        context: FMLJavaModLoadingContext,
    ) {
        info("Registering blocks for ${CreateHarmonicsMod.MOD_ID}")
    }
}
