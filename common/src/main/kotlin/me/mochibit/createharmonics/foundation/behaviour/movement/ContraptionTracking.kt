package me.mochibit.createharmonics.foundation.behaviour.movement

import com.simibubi.create.api.behaviour.movement.MovementBehaviour
import com.simibubi.create.content.contraptions.AbstractContraptionEntity
import me.mochibit.createharmonics.foundation.eventbus.AutoHandler
import me.mochibit.createharmonics.foundation.eventbus.ModEventHandler
import me.mochibit.createharmonics.foundation.services.eventService
import java.util.UUID

@AutoHandler
object ContraptionTracking : ModEventHandler {
    private val trackers = HashMap<Int, MutableSet<UUID>>()

    override fun setupEvents() {
        eventService.onPlayerStartTrackingEntity { player, entity ->
            if (entity !is AbstractContraptionEntity) return@onPlayerStartTrackingEntity
            trackers.getOrPut(entity.id) { HashSet() }.add(player.uuid)
            entity.smartActors().forEach { (b, ctx) -> b.resyncData(ctx) }
        }
        eventService.onPlayerStopTrackingEntity { player, entity ->
            if (entity !is AbstractContraptionEntity) return@onPlayerStopTrackingEntity
            val set = trackers[entity.id] ?: return@onPlayerStopTrackingEntity
            if (!set.remove(player.uuid) || set.isNotEmpty()) return@onPlayerStopTrackingEntity
            trackers.remove(entity.id)
            entity.smartActors().forEach { (b, ctx) -> b.notifyStoppedTracking(ctx) }
        }
    }

    private fun AbstractContraptionEntity.smartActors() =
        contraption.actors.mapNotNull { (info, ctx) ->
            (MovementBehaviour.REGISTRY.get(info.state) as? SmartMovementBehaviour<*>)?.let { it to ctx }
        }
}