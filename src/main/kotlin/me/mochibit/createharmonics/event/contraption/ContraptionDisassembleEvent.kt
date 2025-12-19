package me.mochibit.createharmonics.event.contraption

import com.simibubi.create.content.contraptions.Contraption
import net.minecraftforge.eventbus.api.Event

class ContraptionDisassembleEvent(
    val contraptionId: Int,
) : Event()
