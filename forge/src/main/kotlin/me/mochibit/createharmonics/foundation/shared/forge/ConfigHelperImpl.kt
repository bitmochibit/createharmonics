package me.mochibit.createharmonics.foundation.shared.forge

import me.mochibit.createharmonics.ServerConfig
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.foundation.registry.ModConfigurations

object ConfigHelperImpl {
    @JvmStatic
    fun getRecordDurability(recordType: RecordType): Int? = ServerConfig.getRecordDurability(recordType)

    @JvmStatic
    fun getYtdlpOverrideArgs(): String = ModConfigurations.client.ytdlpOverrideArgs.get()
}
