@file:Suppress("RedundantNullableReturnType")

package me.mochibit.createharmonics.foundation.shared

import dev.architectury.injectables.annotations.ExpectPlatform
import me.mochibit.createharmonics.content.records.RecordType

object ConfigHelper {
    @JvmStatic
    @ExpectPlatform
    fun getRecordDurability(recordType: RecordType): Int? = throw AssertionError("Platform-specific implementation required")

    @JvmStatic
    @ExpectPlatform
    fun getYtdlpOverrideArgs(): String = throw AssertionError("Platform-specific implementation required")
}
