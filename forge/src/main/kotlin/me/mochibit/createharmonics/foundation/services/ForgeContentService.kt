package me.mochibit.createharmonics.foundation.services

import com.simibubi.create.content.contraptions.AbstractContraptionEntity
import com.simibubi.create.foundation.utility.AdventureUtil
import me.mochibit.createharmonics.audio.instance.SimpleShipStreamSoundInstance
import me.mochibit.createharmonics.audio.instance.SimpleStreamSoundInstance
import me.mochibit.createharmonics.audio.instance.StreamingSoundInstance
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlockEntity
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerMovementBehaviour
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerMovementBehaviourTracker
import me.mochibit.createharmonics.content.processing.recordPressBase.RecordPressBaseBlockEntity
import me.mochibit.createharmonics.content.records.BaseRecordItem
import me.mochibit.createharmonics.content.records.EtherealRecordItem
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.foundation.behaviour.movement.handleBlockDataChange
import me.mochibit.createharmonics.foundation.extension.getManagingShip
import me.mochibit.createharmonics.foundation.registry.ModBlocks
import me.mochibit.createharmonics.foundation.registry.ModItems
import me.mochibit.createharmonics.foundation.registry.ModPonders
import me.mochibit.createharmonics.foundation.registry.ModSounds
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
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.material.FluidState
import java.io.InputStream

class ForgeContentService : ContentService {
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
        checkForVsShip: Boolean,
        vsLevelCheck: Level?,
        vsBeCheck: BlockEntity?,
    ): StreamingSoundInstance {
        ItemStack(Items.GOLD_ORE).serializeNBT()
        if (checkForVsShip && vsLevelCheck != null && vsBeCheck != null) {
            val ship = vsBeCheck.blockPos.getManagingShip(vsLevelCheck)
            if (ship != null) {
                return SimpleShipStreamSoundInstance(
                    stream,
                    streamId,
                    soundEvent,
                    posSupplier,
                    ship,
                    volumeSupplier,
                    pitchSupplier,
                    radiusSupplier,
                    randomSource,
                    soundSource,
                    sampleRate = sampleRate,
                )
            }
        }
        return SimpleStreamSoundInstance(
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
    }

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

    override fun onStreamEnd(
        audioPlayerId: String,
        failure: Boolean,
    ): Boolean {
        RecordPlayerBlockEntity.handlePlaybackEnd(audioPlayerId, failure)

        RecordPlayerMovementBehaviourTracker.get(audioPlayerId)?.let { movementContext ->
            RecordPlayerMovementBehaviour.Companion.Utils.handlePlaybackEnd(movementContext)
        }
        return true
    }

    override fun onTitleChange(
        audioPlayerId: String,
        audioName: String,
    ): Boolean {
        RecordPlayerBlockEntity.handleAudioTitleChange(audioPlayerId, audioName)
        return true
    }

    override fun isEtherealRecord(stack: ItemStack): Boolean = stack.item is EtherealRecordItem

    override fun getEtherealRecordType(stack: ItemStack): RecordType? {
        val etherealRecord = stack.item as? EtherealRecordItem ?: return null
        return etherealRecord.recordType
    }

    override fun isEtherealRecordDamageable(stack: ItemStack): Boolean {
        val etherealRecord = stack.item as? EtherealRecordItem ?: return false
        return etherealRecord.isDamageable(stack)
    }

    override fun isRecordBase(stack: ItemStack): Boolean = stack.item is BaseRecordItem
}
