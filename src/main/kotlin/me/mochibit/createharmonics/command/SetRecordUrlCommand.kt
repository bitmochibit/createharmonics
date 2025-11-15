package me.mochibit.createharmonics.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import me.mochibit.createharmonics.content.item.EtherealRecordItem
import me.mochibit.createharmonics.content.item.EtherealRecordItem.Companion.setAudioUrl
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player

/**
 * Debug command to get a record item with urls and stuff
 */
object SetRecordUrlCommand : Registrable<CommandDispatcher<CommandSourceStack>> {
    override fun register(ctx: CommandDispatcher<CommandSourceStack>) {
        ctx.register(
            Commands
                .literal("set-record-url")
                .requires { it.hasPermission(4) } // Admin only
                .then(
                    Commands.argument("url", StringArgumentType.string())
                        .executes(this::execute)
                )
        )
    }

    /**
     * If the player is holding an Ethereal Record, set its audio URL to the provided URL.
     */
    private fun execute(ctx: CommandContext<CommandSourceStack>): Int {
        val audioUrl = StringArgumentType.getString(ctx, "url")
        val source = ctx.source

        val player = source.entity
        if (player == null) {
            source.sendFailure(Component.literal("This command can only be used by a player."))
            return 0
        }

        if (player !is Player) {
            source.sendFailure(Component.literal("This command can only be used by a player."))
            return 0
        }

        val mainHandItem = player.mainHandItem
        if (mainHandItem.item !is EtherealRecordItem) {
            source.sendFailure(Component.literal("You must be holding an Ethereal Record (main hand) to use this command."))
            return 0
        }


        setAudioUrl(mainHandItem, audioUrl)

        source.sendSuccess(
            {
                Component.literal("Set Ethereal Record audio URL to: $audioUrl")
            },
            false
        )

        return 1
    }

}
