package me.mochibit.createharmonics.foundation.registry

import me.mochibit.createharmonics.command.CommandEntry
import me.mochibit.createharmonics.foundation.info
import me.mochibit.createharmonics.foundation.services.eventService
import net.minecraft.core.Registry

@AutoRegister
object ModCommands : Registrable {
    override fun register(registry: Registry<*>?) {
        "Pointing mod commands for registration".info()
        eventService.onRegisterCommands { dispatcher, env, context ->
            CommandEntry.registerAll(dispatcher, env, context)

        }
    }
}
