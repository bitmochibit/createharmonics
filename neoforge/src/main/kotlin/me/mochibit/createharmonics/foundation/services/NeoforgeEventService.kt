package me.mochibit.createharmonics.foundation.services

import com.mojang.brigadier.CommandDispatcher
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity
import com.simibubi.create.content.kinetics.deployer.DeployerRecipeSearchEvent
import me.mochibit.createharmonics.ModEventBus
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.core.RegistryAccess
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeHolder
import net.minecraft.world.item.crafting.RecipeInput
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.fml.config.ModConfig
import net.neoforged.fml.event.config.ModConfigEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.GameShuttingDownEvent
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.TagsUpdatedEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.level.LevelEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.items.wrapper.RecipeWrapper
import java.util.*
import java.util.function.Supplier

class NeoforgeEventService : EventService {
    override fun onServerStarted(listener: (server: MinecraftServer) -> Unit) {
        NeoForge.EVENT_BUS.addListener<ServerStartedEvent> { e ->
            listener(e.server)
        }
    }

    override fun onServerStopped(listener: (server: MinecraftServer) -> Unit) {
        NeoForge.EVENT_BUS.addListener<ServerStoppedEvent> { e ->
            listener(e.server)
        }
    }

    override fun onGameShuttingDown(listener: () -> Unit) {
        NeoForge.EVENT_BUS.addListener<GameShuttingDownEvent> { e ->
            listener()
        }
    }

    override fun onPlayerStartTrackingEntity(listener: (player: ServerPlayer, entity: Entity) -> Unit) {
        NeoForge.EVENT_BUS.addListener<PlayerEvent.StartTracking> { e ->
            listener(e.entity as ServerPlayer, e.target)
        }
    }

    override fun onPlayerStopTrackingEntity(listener: (player: ServerPlayer, entity: Entity) -> Unit) {
        NeoForge.EVENT_BUS.addListener<PlayerEvent.StopTracking> { e ->
            listener(e.entity as ServerPlayer, e.target)
        }
    }

    override fun onEntityJoinLevel(listener: (entity: Entity, level: Level) -> Unit) {
        NeoForge.EVENT_BUS.addListener<EntityJoinLevelEvent> { e ->
            listener(e.entity, e.level)
        }
    }

    override fun onLevelUnload(listener: (levelAccess: LevelAccessor) -> Unit) {
        NeoForge.EVENT_BUS.addListener<LevelEvent.Unload> { e ->
            listener(e.level)
        }
    }

    override fun onBlockNeighborNotify(listener: (level: Level, pos: BlockPos, state: BlockState) -> Unit) {
        NeoForge.EVENT_BUS.addListener<BlockEvent.NeighborNotifyEvent> { e ->
            val level = e.level
            if (level is Level) {
                listener(level, e.pos, e.state)
            }
        }
    }

    override fun onRegisterCommands(listener: (dispatcher: CommandDispatcher<CommandSourceStack>, env: Commands.CommandSelection, context: CommandBuildContext) -> Unit) {
        NeoForge.EVENT_BUS.addListener<RegisterCommandsEvent> { e ->
            listener(e.dispatcher, e.commandSelection, e.buildContext)
        }
    }

    override fun onCreateDeployerRecipeSearch(listener: (deployerBe: DeployerBlockEntity, recipeWrapper: RecipeWrapper, addRecipe: (Supplier<Optional<out RecipeHolder<out Recipe<out RecipeInput>>>>, Int) -> Unit) -> Unit) {
        NeoForge.EVENT_BUS.addListener<DeployerRecipeSearchEvent> { e ->
            listener(
                e.blockEntity,
                e.inventory,
                e::addRecipe
            )
        }
    }

    override fun onTagsUpdated(listener: (registryAccess: RegistryAccess) -> Unit) {
        NeoForge.EVENT_BUS.addListener<TagsUpdatedEvent> { e ->
            listener(e.registryAccess)
        }
    }


    override fun onConfigReload(listener: (configType: ConfigEvents.ConfigType) -> Unit) {
        ModEventBus.addListener<ModConfigEvent.Reloading> { e ->
            val convertedConfigType = when (e.config.type) {
                ModConfig.Type.COMMON -> ConfigEvents.ConfigType.COMMON
                ModConfig.Type.CLIENT -> ConfigEvents.ConfigType.CLIENT
                ModConfig.Type.SERVER -> ConfigEvents.ConfigType.SERVER
                ModConfig.Type.STARTUP -> ConfigEvents.ConfigType.STARTUP
            }
            listener(convertedConfigType)
        }
    }

}