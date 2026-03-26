package me.mochibit.createharmonics.foundation.registry

import com.simibubi.create.AllTags
import com.simibubi.create.api.behaviour.display.DisplaySource.displaySource
import com.simibubi.create.api.behaviour.movement.MovementBehaviour.movementBehaviour
import com.simibubi.create.api.contraption.storage.item.MountedItemStorageType.mountedItemStorage
import com.simibubi.create.foundation.data.AssetLookup
import com.simibubi.create.foundation.data.AssetLookup.customItemModel
import com.simibubi.create.foundation.data.BlockStateGen
import com.simibubi.create.foundation.data.ModelGen.customItemModel
import com.simibubi.create.foundation.data.SharedProperties
import com.tterrag.registrate.util.entry.BlockEntry
import me.mochibit.createharmonics.ModRegistrate
import me.mochibit.createharmonics.config.ModStressConfig
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerMovementBehaviour
import me.mochibit.createharmonics.content.kinetics.recordPlayer.andesiteJukebox.AndesiteJukeboxBlock
import me.mochibit.createharmonics.content.kinetics.recordPlayer.brassJukebox.BrassJukeboxBlock
import me.mochibit.createharmonics.content.processing.recordPressBase.RecordPressBaseBlock
import me.mochibit.createharmonics.foundation.info
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.level.block.SoundType
import java.util.function.Supplier

object ModBlocks : CommonRegistry {
    override val registrationOrder = 2

    val ANDESITE_JUKEBOX: BlockEntry<AndesiteJukeboxBlock> =
        ModRegistrate
            .block("andesite_jukebox") { properties ->
                AndesiteJukeboxBlock(properties)
            }.initialProperties { SharedProperties.wooden() }
            .properties { p ->
                p
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.WOOD)
            }.onRegister(movementBehaviour(RecordPlayerMovementBehaviour()))
            .tag(
                AllTags.AllBlockTags.SAFE_NBT.tag,
            ).tag(AllTags.AllBlockTags.SIMPLE_MOUNTED_STORAGE.tag)
            .transform(mountedItemStorage(ModMountedStorages.SIMPLE_RECORD_PLAYER_STORAGE))
            .transform(displaySource(ModDisplaySources.AUDIO_NAME))
            .transform(ModStressConfig.setImpact(1.0))
            .blockstate(BlockStateGen.directionalBlockProvider(true))
            .item()
            .tag(AllTags.AllItemTags.CONTRAPTION_CONTROLLED.tag)
            .transform(customItemModel())
            .register()

    val BRASS_JUKEBOX: BlockEntry<BrassJukeboxBlock> =
        ModRegistrate
            .block("brass_jukebox") { properties ->
                BrassJukeboxBlock(properties)
            }.properties { p ->
                p
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
            }.blockstate(BlockStateGen.horizontalBlockProvider(true))
            .onRegister(movementBehaviour(RecordPlayerMovementBehaviour()))
            .tag(
                AllTags.AllBlockTags.SAFE_NBT.tag,
            ).tag(AllTags.AllBlockTags.SIMPLE_MOUNTED_STORAGE.tag)
            .transform(mountedItemStorage(ModMountedStorages.SIMPLE_RECORD_PLAYER_STORAGE))
            .transform(displaySource(ModDisplaySources.AUDIO_NAME))
            .transform(ModStressConfig.setImpact(1.0))
            .item()
            .tag(AllTags.AllItemTags.CONTRAPTION_CONTROLLED.tag)
            .transform(customItemModel())
            .register()

    val RECORD_PRESS_BASE: BlockEntry<RecordPressBaseBlock> =
        ModRegistrate
            .block("record_press_base", ::RecordPressBaseBlock)
            .properties { p ->
                p
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.COPPER)
            }.tag(
                AllTags.AllBlockTags.SAFE_NBT.tag,
            ).blockstate { ctx, p ->
                p.simpleBlock(ctx.entry, AssetLookup.partialBaseModel(ctx, p))
            }.item()
            .transform(customItemModel())
            .register()

    override fun register() {
        "Registering blocks".info()
    }
}
