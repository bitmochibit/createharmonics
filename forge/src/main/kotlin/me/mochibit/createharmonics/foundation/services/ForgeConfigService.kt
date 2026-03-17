package me.mochibit.createharmonics.foundation.services

import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.foundation.registry.ModConfigurations
import net.minecraftforge.fml.config.ModConfig
import kotlin.text.get

class ForgeConfigService : ConfigService {
    override fun getRecordDurability(recordType: RecordType): Int? = ModConfigurations.server.getRecordDurability(recordType)

    override fun getYtdlpOverrideArgs(): String = ModConfigurations.client.ytdlpOverrideArgs.get()

    override fun getNeverShowLibraryDisclaimer(): Boolean = ModConfigurations.client.neverShowLibraryDisclaimer.get()

    override fun setNeverShowLibraryDisclaimer(value: Boolean) = ModConfigurations.client.neverShowLibraryDisclaimer.set(value)
}
