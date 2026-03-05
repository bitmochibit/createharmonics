package me.mochibit.createharmonics.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import me.mochibit.createharmonics.command.SetRecordUrlCommand.registerCommand
import me.mochibit.createharmonics.content.records.RecordUtilities.setAudioUrl
import me.mochibit.createharmonics.foundation.info
import me.mochibit.createharmonics.foundation.services.contentService
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player

sealed interface CommandEntry {
    fun registerCommand(
        dispatcher: CommandDispatcher<CommandSourceStack>,
        env: Commands.CommandSelection,
        context: CommandBuildContext,
    )

    companion object {
        fun registerAll(
            dispatcher: CommandDispatcher<CommandSourceStack>,
            env: Commands.CommandSelection,
            context: CommandBuildContext,
        ) {
            "Registering mod events..".info()
            CommandEntry::class
                .sealedSubclasses
                .filter { CommandEntry::class.java.isAssignableFrom(it.java) }
                .map { it.objectInstance as CommandEntry }
                .forEach { _ -> registerCommand(dispatcher, env, context) }
        }
    }
}

object SetRecordUrlCommand : CommandEntry {
    override fun registerCommand(
        dispatcher: CommandDispatcher<CommandSourceStack>,
        env: Commands.CommandSelection,
        context: CommandBuildContext,
    ) {
        dispatcher.register(
            Commands
                .literal("set-record-url")
                .requires { it.hasPermission(4) } // Admin only
                .then(
                    Commands
                        .argument("url", StringArgumentType.string())
                        .executes(this::execute),
                ),
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
        if (!contentService.isEtherealRecord(mainHandItem)) {
            source.sendFailure(Component.literal("You must be holding an Ethereal Record (main hand) to use this command."))
            return 0
        }

        setAudioUrl(mainHandItem, audioUrl)

        source.sendSuccess(
            {
                Component.literal("Set Ethereal Record audio URL to: $audioUrl")
            },
            false,
        )

        return 1
    }
}
