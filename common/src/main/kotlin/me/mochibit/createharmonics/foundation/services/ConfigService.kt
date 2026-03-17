package me.mochibit.createharmonics.foundation.services

import me.mochibit.createharmonics.content.records.RecordType

interface ConfigService {
    fun getRecordDurability(recordType: RecordType): Int?

    fun getYtdlpOverrideArgs(): String

    fun getNeverShowLibraryDisclaimer(): Boolean

    fun setNeverShowLibraryDisclaimer(value: Boolean)
}

val configService: ConfigService by lazy {
    loadService<ConfigService>()
}
