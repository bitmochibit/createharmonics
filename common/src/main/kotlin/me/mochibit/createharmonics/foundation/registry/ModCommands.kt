package me.mochibit.createharmonics.foundation.registry

import me.mochibit.createharmonics.command.CommandEntry
import me.mochibit.createharmonics.foundation.eventbus.CommonEvents
import me.mochibit.createharmonics.foundation.eventbus.EventBus
import me.mochibit.createharmonics.foundation.eventbus.ProxyEvent
import me.mochibit.createharmonics.foundation.info
import net.minecraft.core.Registry

object ModCommands : CommonRegistry {
    override fun register(registry: Registry<*>?) {
        "Pointing mod commands for registration".info()
        EventBus.on<CommonEvents.RegisterCommandsEvent> { event ->
            CommandEntry.registerAll(event.dispatcher, event.env, event.context)
        }
    }
}
