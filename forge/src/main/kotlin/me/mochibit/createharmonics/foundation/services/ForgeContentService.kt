package me.mochibit.createharmonics.foundation.services

import com.simibubi.create.content.contraptions.AbstractContraptionEntity
import me.mochibit.createharmonics.foundation.network.packet.ContraptionBlockDataChangedPacket
import me.mochibit.createharmonics.foundation.registry.ForgeModPackets
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo
import net.minecraftforge.network.PacketDistributor

class ForgeContentService : ContentService {
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
        blockPos: BlockPos,
        audioUrls: MutableList<String>,
        urlWeights: MutableList<Float>,
        randomMode: Boolean,
        newIndex: Int,
    ) {
        be.audioUrls = audioUrls
        be.urlWeights = urlWeights
        be.randomMode = randomMode
        be.currentUrlIndex = newIndex
    }
}
