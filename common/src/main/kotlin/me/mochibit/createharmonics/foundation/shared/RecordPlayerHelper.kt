package me.mochibit.createharmonics.foundation.shared

import dev.architectury.injectables.annotations.ExpectPlatform

object RecordPlayerHelper {
    @JvmStatic
    @ExpectPlatform
    fun onStreamEnd(
        audioPlayerId: String,
        failure: Boolean,
    ): Boolean = throw AssertionError("Platform-specific implementation required")

    @JvmStatic
    @ExpectPlatform
    fun onTitleChange(
        audioPlayerId: String,
        audioName: String,
    ): Boolean = throw AssertionError("Platform-specific implementation required")
}
