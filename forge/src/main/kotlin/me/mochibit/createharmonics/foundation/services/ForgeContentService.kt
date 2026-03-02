package me.mochibit.createharmonics.foundation.services

import com.simibubi.create.content.contraptions.AbstractContraptionEntity
import com.simibubi.create.foundation.utility.AdventureUtil
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerBlockEntity
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerMovementBehaviour
import me.mochibit.createharmonics.content.processing.recordPressBase.RecordPressBaseBlockEntity
import me.mochibit.createharmonics.foundation.network.packet.ContraptionBlockDataChangedPacket
import me.mochibit.createharmonics.foundation.registry.ForgeModPackets
import me.mochibit.createharmonics.foundation.registry.ModPonders
import me.mochibit.createharmonics.foundation.registry.ModSounds
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo
import net.minecraft.world.level.material.FluidState
import net.minecraftforge.network.PacketDistributor

class ForgeContentService : ContentService {
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

    override val slidingStoneSound: SoundEvent by lazy { ModSounds.SLIDING_STONE.get() }
    override val glitterSoundEvent: SoundEvent by lazy { ModSounds.GLITTER.get() }

    override fun onStreamEnd(
        audioPlayerId: String,
        failure: Boolean,
    ): Boolean {
        RecordPlayerBlockEntity.handlePlaybackEnd(audioPlayerId, failure)

        RecordPlayerMovementBehaviour.getContextByPlayerUUID(audioPlayerId)?.let { movementContext ->
            RecordPlayerMovementBehaviour.stopMovingPlayer(movementContext)
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
}

fun AbstractContraptionEntity.handleBlockDataChange(
    localPos: BlockPos,
    newData: CompoundTag,
) {
    if (contraption == null || !contraption.blocks.containsKey(localPos)) return
    val info: StructureBlockInfo = contraption.blocks[localPos] ?: return
    contraption.blocks[localPos] = StructureBlockInfo(info.pos(), info.state, newData)
}

/**
 * Easily synchronizes blockdata server to client
 */
fun AbstractContraptionEntity.setBlockData(
    localPos: BlockPos,
    newInfo: StructureBlockInfo,
) {
    contraption.blocks[localPos] = newInfo
    ForgeModPackets.channel.send(
        PacketDistributor.TRACKING_ENTITY.with { this },
        ContraptionBlockDataChangedPacket(id, localPos, newInfo.nbt ?: CompoundTag()),
    )
}
