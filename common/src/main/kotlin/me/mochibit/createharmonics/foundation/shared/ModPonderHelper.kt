package me.mochibit.createharmonics.foundation.shared

import dev.architectury.injectables.annotations.ExpectPlatform
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper
import net.minecraft.resources.ResourceLocation

object ModPonderHelper {
    @JvmStatic
    @ExpectPlatform
    fun addTags(rawHelper: PonderTagRegistrationHelper<ResourceLocation>): Unit =
        throw AssertionError("Platform-specific implementation required")

    @JvmStatic
    @ExpectPlatform
    fun addScenes(rawHelper: PonderSceneRegistrationHelper<ResourceLocation>): Unit =
        throw AssertionError("Platform-specific implementation required")
}
