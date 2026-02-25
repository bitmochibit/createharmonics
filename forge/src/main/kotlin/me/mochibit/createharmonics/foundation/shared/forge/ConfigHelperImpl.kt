package me.mochibit.createharmonics.foundation.shared.forge

import me.mochibit.createharmonics.ServerConfig
import me.mochibit.createharmonics.content.records.RecordType

object ConfigHelperImpl {
    @JvmStatic
    fun getRecordDurability(recordType: RecordType): Int? = ServerConfig.getRecordDurability(recordType)
}
