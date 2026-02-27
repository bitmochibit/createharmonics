package me.mochibit.createharmonics.foundation.registry

import com.simibubi.create.api.registry.CreateRegistries
import com.simibubi.create.content.kinetics.mechanicalArm.AllArmInteractionPointTypes
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType
import com.tterrag.registrate.util.entry.RegistryEntry
import me.mochibit.createharmonics.cRegistrate
import me.mochibit.createharmonics.content.kinetics.recordPlayer.RecordPlayerArmPoint
import me.mochibit.createharmonics.content.processing.recordPressBase.RecordPressBaseArmInteractionPoint
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState

object ModArmInteractionPoints : Registrable {
    override val registrationOrder = 3

    val RECORD_PLAYER_TYPE: RegistryEntry<RecordPlayerType> =
        cRegistrate()
            .generic("record_player", CreateRegistries.ARM_INTERACTION_POINT_TYPE) {
                RecordPlayerType()
            }.register()

    val RECORD_PRESS_BASE_TYPE: RegistryEntry<RecordPressBaseType> =
        cRegistrate()
            .generic("record_press_base", CreateRegistries.ARM_INTERACTION_POINT_TYPE) {
                RecordPressBaseType()
            }.register()

    override fun register() {
    }

    class RecordPressBaseType : ArmInteractionPointType() {
        override fun canCreatePoint(
            level: Level,
            pos: BlockPos,
            state: BlockState,
        ): Boolean = state.`is`(ModBlocks.RECORD_PRESS_BASE.get())

        override fun createPoint(
            level: Level,
            pos: BlockPos,
            state: BlockState,
        ): ArmInteractionPoint = RecordPressBaseArmInteractionPoint(this, level, pos, state)
    }

    class RecordPlayerType : AllArmInteractionPointTypes.JukeboxType() {
        override fun canCreatePoint(
            level: Level?,
            pos: BlockPos?,
            state: BlockState,
        ): Boolean = state.`is`(ModBlocks.ANDESITE_JUKEBOX.get())

        override fun createPoint(
            level: Level,
            pos: BlockPos,
            state: BlockState,
        ): ArmInteractionPoint = RecordPlayerArmPoint(this, level, pos, state)
    }
}
