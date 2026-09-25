package me.mochibit.createharmonics.content.uploader

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import me.mochibit.createharmonics.audio.soundscape.ModSoundScapes
import me.mochibit.createharmonics.content.processing.recordPressBase.RecordPressBaseBehaviour
import me.mochibit.createharmonics.foundation.extension.onClient
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties

class AmethystCatalystBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
): SmartBlockEntity(type, pos, state) {

    lateinit var behaviour: AmethystCatalystBehaviour
        private set

    override fun addBehaviours(behaviours: MutableList<BlockEntityBehaviour>) {
        behaviour = AmethystCatalystBehaviour(this)
        behaviours.add(behaviour)
    }


    override fun tick() {
        super.tick()

        this.level?.onClient { level, virtual ->
            if (behaviour.hasCrystal) {
                ModSoundScapes.play(ModSoundScapes.AmbienceGroup.RESONATING, worldPosition, 0.5f)
            }
        }
    }
}