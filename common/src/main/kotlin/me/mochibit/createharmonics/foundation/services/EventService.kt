package me.mochibit.createharmonics.foundation.services

import com.mojang.brigadier.CommandDispatcher
import com.mojang.serialization.Codec
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeHolder
import net.minecraft.world.item.crafting.RecipeInput
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.items.wrapper.RecipeWrapper
import java.util.*
import java.util.function.Supplier

/**
 * Server events
 *
 */
interface ServerEvents {
    /**
     * On server started
     *
     * @param listener
     * @receiver
     */
    fun onServerStarted(listener: (server: MinecraftServer) -> Unit)

    /**
     * On server stopped
     *
     * @param listener
     * @receiver
     */
    fun onServerStopped(listener: (server: MinecraftServer) -> Unit)

    /**
     * On game shutting down
     *
     * @param listener
     * @receiver
     */
    fun onGameShuttingDown(listener: () -> Unit)

    /**
     * On player join
     *
     * @param listener
     * @receiver
     */
    fun onPlayerJoin(listener: (player: Player) -> Unit)

    /**
     * On player logout
     *
     * @param listener
     * @receiver
     */
    fun onPlayerLogout(listener: (player: Player) -> Unit)

    /**
     * On server tick
     *
     * @param listener
     * @receiver
     */
    fun onServerTick(listener: (server: MinecraftServer) -> Unit)
}

/**
 * Player tracking events
 *
 */
interface PlayerTrackingEvents {
    /**
     * On player start tracking entity
     *
     * @param listener
     * @receiver
     */
    fun onPlayerStartTrackingEntity(listener: (player: ServerPlayer, entity: Entity) -> Unit)

    /**
     * On player stop tracking entity
     *
     * @param listener
     * @receiver
     */
    fun onPlayerStopTrackingEntity(listener: (player: ServerPlayer, entity: Entity) -> Unit)
}

/**
 * Level events
 *
 */
interface LevelEvents {
    /**
     * On entity join level
     *
     * @param listener
     * @receiver
     */
    fun onEntityJoinLevel(listener: (entity: Entity, level: Level) -> Unit)

    /**
     * On level unload
     *
     * @param listener
     * @receiver
     */
    fun onLevelUnload(listener: (levelAccess: LevelAccessor) -> Unit)

    /**
     * On block neighbor notify
     *
     * @param listener
     * @receiver
     */
    fun onBlockNeighborNotify(listener: (level: Level, pos: BlockPos, state: BlockState) -> Unit)
}

/**
 * Data events
 *
 */
interface DataEvents {
    /**
     * On tags updated
     *
     * @param listener
     * @receiver
     */
    fun onTagsUpdated(listener: (registryAccess: RegistryAccess) -> Unit)
}

/**
 * Config events
 *
 */
interface ConfigEvents {
    /**
     * On config reload
     *
     * @param listener
     * @receiver
     */
    fun onConfigReload(listener: (modId: String, configType: ConfigType) -> Unit)

    /**
     * Config type
     * Enum — a fixed set of named constants.
     *
     * @constructor Creates a new ConfigType
     */
    enum class ConfigType {
        /**
         * Common
         *
         * @constructor Creates a new COMMON
         */
        COMMON,

        /**
         * Client
         *
         * @constructor Creates a new CLIENT
         */
        CLIENT,

        /**
         * Server
         *
         * @constructor Creates a new SERVER
         */
        SERVER,

        /**
         * Startup
         *
         * @constructor Creates a new STARTUP
         */
        STARTUP
    }
}

/**
 * Command events
 *
 */
interface CommandEvents {
    /**
     * On register commands
     *
     * @param listener
     * @receiver
     */
    fun onRegisterCommands(
        listener: (dispatcher: CommandDispatcher<CommandSourceStack>, env: Commands.CommandSelection, context: CommandBuildContext) -> Unit
    )
}

/**
 * Creates a new events
 *
 */
interface CreateEvents {
    /**
     * On create deployer recipe search
     *
     * @param listener
     * @receiver
     */
    fun onCreateDeployerRecipeSearch(
        listener: (
            deployerBe: DeployerBlockEntity,
            recipeWrapper: RecipeWrapper,
            addRecipe: (Supplier<Optional<out RecipeHolder<out Recipe<out RecipeInput>>>>, Int) -> Unit,
        ) -> Unit
    )
}

/**
 * Event service
 *
 */
interface EventService :
    ServerEvents,
    PlayerTrackingEvents,
    LevelEvents,
    CommandEvents,
    CreateEvents,
    DataEvents,
    ConfigEvents

val eventService: EventService by lazy { loadService<EventService>() }
