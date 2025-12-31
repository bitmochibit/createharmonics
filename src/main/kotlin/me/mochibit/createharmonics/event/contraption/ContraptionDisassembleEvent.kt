package me.mochibit.createharmonics.event.contraption

import com.simibubi.create.content.contraptions.Contraption
import com.simibubi.create.content.contraptions.behaviour.MovementContext
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import net.minecraftforge.eventbus.api.Event
import org.apache.commons.lang3.tuple.MutablePair

class ContraptionDisassembleEvent(
    val contraptionId: Int,
    val level: Level,
    val blockEntityDataMap: Map<BlockPos, CompoundTag>,
) : Event()
