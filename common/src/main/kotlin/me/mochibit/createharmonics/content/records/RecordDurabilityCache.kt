package me.mochibit.createharmonics.content.records

import me.mochibit.createharmonics.config.ModConfigs
import me.mochibit.createharmonics.content.records.properties.RecordProperties
import me.mochibit.createharmonics.foundation.eventbus.AutoHandler
import me.mochibit.createharmonics.foundation.eventbus.ModEventHandler
import me.mochibit.createharmonics.foundation.services.ConfigEvents
import me.mochibit.createharmonics.foundation.services.eventService
import net.minecraft.core.RegistryAccess

@AutoHandler
object RecordDurabilityCache: ModEventHandler {
    @Volatile private var access: RegistryAccess? = null
    @Volatile private var uses: Map<RecordType, Int> = emptyMap()
    operator fun get(type: RecordType): Int = uses[type] ?: RecordProperties.FALLBACK.durability


    private fun rebuild() {
        val access = access ?: return
        uses = RecordType.entries.associateWith { type ->
            val props = type.properties(access)
            when {
                props.unbreakable -> 0
                else -> ModConfigs.server.getRecordDurability(type) ?: props.durability
            }
        }
    }

    override fun setupEvents() {
        eventService.onTagsUpdated { registryAccess ->
            access = registryAccess
            rebuild()
        }

        eventService.onConfigReload { configType ->
            if (configType == ConfigEvents.ConfigType.SERVER) {
                rebuild()
            }
        }
    }
}