package me.mochibit.createharmonics.foundation.services

import com.mojang.brigadier.CommandDispatcher
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeHolder
import net.minecraft.world.item.crafting.RecipeInput
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.neoforged.neoforge.items.wrapper.RecipeWrapper
import java.util.Optional
import java.util.function.Supplier

interface ServerEvents {
    fun onServerStarted(listener: (server: MinecraftServer) -> Unit)
    fun onServerStopped(listener: (server: MinecraftServer) -> Unit)
    fun onGameShuttingDown(listener: () -> Unit)
}

interface PlayerTrackingEvents {
    fun onPlayerStartTrackingEntity(listener: (player: ServerPlayer, entity: Entity) -> Unit)
    fun onPlayerStopTrackingEntity(listener: (player: ServerPlayer, entity: Entity) -> Unit)
}

interface LevelEvents {
    fun onEntityJoinLevel(listener: (entity: Entity, level: Level) -> Unit)
    fun onLevelUnload(listener: (levelAccess: LevelAccessor) -> Unit)
}

interface CommandEvents {
    fun onRegisterCommands(
        listener: (dispatcher: CommandDispatcher<CommandSourceStack>, env: Commands.CommandSelection, context: CommandBuildContext) -> Unit
    )
}

interface CreateEvents {
    fun onCreateDeployerRecipeSearch(
        listener: (
            deployerBe: DeployerBlockEntity,
            recipeWrapper: RecipeWrapper,
            addRecipe: (Supplier<Optional<out RecipeHolder<out Recipe<out RecipeInput>>>>, Int) -> Unit,
        ) -> Unit
    )
}

interface EventService :
    ServerEvents,
    PlayerTrackingEvents,
    LevelEvents,
    CommandEvents,
    CreateEvents

val eventService: EventService by lazy { loadService<EventService>() }
