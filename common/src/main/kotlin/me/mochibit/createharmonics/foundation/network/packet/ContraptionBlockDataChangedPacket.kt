package me.mochibit.createharmonics.foundation.network.packet

import com.simibubi.create.content.contraptions.AbstractContraptionEntity
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import me.mochibit.createharmonics.foundation.async.modLaunch
import me.mochibit.createharmonics.foundation.behaviour.movement.handleBlockDataChange
import me.mochibit.createharmonics.foundation.services.contentService
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag

/**
 * Contraption block data changed packet
 *
 * @constructor Creates a new ContraptionBlockDataChangedPacket
 * @property entityID the entity id
 * @property localPos the local pos
 * @property newData the new data
 */
@AutoPacket
@Serializable
class ContraptionBlockDataChangedPacket(
    val entityID: Int,
    @Contextual val localPos: BlockPos,
    @Contextual val newData: CompoundTag,
) : S2CPacket {
    override fun handle(context: ClientPacketContext) {
        modLaunch {
            val entity =
                Minecraft.getInstance().level?.getEntity(entityID) as? AbstractContraptionEntity ?: return@modLaunch
            entity.handleBlockDataChange(localPos, newData)
        }
    }
}
