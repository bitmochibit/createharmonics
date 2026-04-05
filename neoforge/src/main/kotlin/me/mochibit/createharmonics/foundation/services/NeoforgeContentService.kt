package me.mochibit.createharmonics.foundation.services

import com.simibubi.create.content.contraptions.AbstractContraptionEntity
import com.simibubi.create.foundation.utility.AdventureUtil
import me.mochibit.createharmonics.audio.instance.SimpleStreamSoundInstance
import me.mochibit.createharmonics.audio.instance.StreamingSoundInstance
import me.mochibit.createharmonics.content.processing.recordPressBase.RecordPressBaseBlockEntity
import me.mochibit.createharmonics.foundation.behaviour.movement.handleBlockDataChange
import me.mochibit.createharmonics.foundation.registry.ModPonders
import me.mochibit.createharmonics.foundation.supplier.values.FloatSupplier
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.level.material.FluidState
import java.io.InputStream

class NeoforgeContentService : ContentService {
    override fun streamingSoundInstanceFactory(
        stream: InputStream,
        streamId: String,
        soundEvent: SoundEvent,
        sampleRate: Int,
        soundSource: SoundSource,
        randomSource: RandomSource,
        volumeSupplier: FloatSupplier,
        pitchSupplier: FloatSupplier,
        posSupplier: () -> BlockPos,
        radiusSupplier: FloatSupplier,
    ): StreamingSoundInstance =
        SimpleStreamSoundInstance(
            stream,
            streamId,
            soundEvent,
            posSupplier,
            radiusSupplier,
            pitchSupplier,
            radiusSupplier,
            randomSource,
            soundSource,
            sampleRate = sampleRate,
        )

    override fun getViscosity(fluidState: FluidState): Int = fluidState.fluidType.viscosity

    override fun addTags(rawHelper: PonderTagRegistrationHelper<ResourceLocation>) {
        ModPonders.addTags(rawHelper)
    }

    override fun addScenes(rawHelper: PonderSceneRegistrationHelper<ResourceLocation>) {
        ModPonders.addScenes(rawHelper)
    }

    override fun contraptionEntityDataChanged(
        entityID: Int,
        localPos: BlockPos,
        newData: CompoundTag,
    ) {
        val entity =
            Minecraft.getInstance().level?.getEntity(entityID) as? AbstractContraptionEntity ?: return
        entity.handleBlockDataChange(localPos, newData)
    }

    override fun configureRecordPressBase(
        sender: ServerPlayer,
        blockPos: BlockPos,
        audioUrls: MutableList<String>,
        urlWeights: MutableList<Float>,
        randomMode: Boolean,
        newIndex: Int,
    ) {
        if (sender.isSpectator || AdventureUtil.isAdventure(sender)) return
        val world = sender.level()
        if (world == null || !world.isLoaded(blockPos)) return
        if (!blockPos.closerThan(sender.blockPosition(), 20.0)) return
        val blockEntity = world.getBlockEntity(blockPos)
        if (blockEntity is RecordPressBaseBlockEntity) {
            blockEntity.audioUrls = audioUrls
            blockEntity.urlWeights = urlWeights
            blockEntity.randomMode = randomMode
            blockEntity.currentUrlIndex = newIndex
            blockEntity.sendData()
            blockEntity.setChanged()
        }
    }
}
