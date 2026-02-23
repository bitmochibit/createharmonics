package me.mochibit.createharmonics.network.packet

import com.simibubi.create.content.contraptions.AbstractContraptionEntity
import com.simibubi.create.foundation.networking.SimplePacketBase
import me.mochibit.createharmonics.registry.ModPackets
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.network.PacketDistributor

@OnlyIn(Dist.CLIENT)
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
    ModPackets.channel.send(
        PacketDistributor.TRACKING_ENTITY.with { this },
        ContraptionBlockDataChangedPacket(id, localPos, newInfo.nbt ?: CompoundTag()),
    )
}

class ContraptionBlockDataChangedPacket(
    val entityID: Int,
    val localPos: BlockPos,
    val newData: CompoundTag,
) : SimplePacketBase() {
    constructor(buffer: FriendlyByteBuf) : this(
        entityID = buffer.readInt(),
        localPos = buffer.readBlockPos(),
        newData = buffer.readNbt() ?: CompoundTag(),
    )

    override fun write(buffer: FriendlyByteBuf) {
        buffer.writeInt(entityID)
        buffer.writeBlockPos(localPos)
        buffer.writeNbt(newData)
    }

    override fun handle(context: NetworkEvent.Context): Boolean {
        context.enqueueWork {
            val entity =
                Minecraft.getInstance().level?.getEntity(entityID) as? AbstractContraptionEntity ?: return@enqueueWork
            entity.handleBlockDataChange(localPos, newData)
        }
        return true
    }
}
