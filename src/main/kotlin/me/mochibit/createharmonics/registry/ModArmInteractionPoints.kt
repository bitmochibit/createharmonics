package me.mochibit.createharmonics.registry

import com.simibubi.create.api.registry.CreateRegistries
import com.simibubi.create.content.kinetics.mechanicalArm.AllArmInteractionPointTypes
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint
import com.tterrag.registrate.util.entry.RegistryEntry
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.cRegistrate
import me.mochibit.createharmonics.content.block.recordPlayer.RecordPlayerArmPoint
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext

object ModArmInteractionPoints : AutoRegistrable {
    val RECORD_PLAYER_TYPE: RegistryEntry<RecordPlayerType> =
        cRegistrate()
            .generic("record_player", CreateRegistries.ARM_INTERACTION_POINT_TYPE) {
                RecordPlayerType()
            }.register()

    override fun register(
        eventBus: IEventBus,
        context: FMLJavaModLoadingContext,
    ) {
        Logger.info("Registering Arm Interaction Points...")
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
