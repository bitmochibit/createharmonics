package me.mochibit.createharmonics.foundation.services

import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.foundation.registry.ModConfigurations
import net.minecraftforge.fml.config.ModConfig

class ForgeConfigService : ConfigService {
    override fun getRecordDurability(recordType: RecordType): Int? = ModConfigurations.server.getRecordDurability(recordType)

    override fun getYtdlpOverrideArgs(): String = ModConfigurations.client.ytdlpOverrideArgs.get()
}
