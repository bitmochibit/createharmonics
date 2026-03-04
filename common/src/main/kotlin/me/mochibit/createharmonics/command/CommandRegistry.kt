package me.mochibit.createharmonics.command

import com.mojang.brigadier.CommandDispatcher
import me.mochibit.createharmonics.foundation.registry.RegistrableWithContext
import net.minecraft.commands.CommandSourceStack

/**
 * Debug command to get a record item with urls and stuff
 */

sealed interface CommandRegistry : RegistrableWithContext<CommandDispatcher<CommandSourceStack>>
