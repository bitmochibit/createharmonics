package me.mochibit.createharmonics.foundation.shared.forge

import me.mochibit.createharmonics.foundation.registry.ModPonders
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper
import net.minecraft.resources.ResourceLocation

object ModPonderHelperImpl {
    @JvmStatic
    fun addTags(rawHelper: PonderTagRegistrationHelper<ResourceLocation>): Unit = ModPonders.addTags(rawHelper)

    @JvmStatic
    fun addScenes(rawHelper: PonderSceneRegistrationHelper<ResourceLocation>): Unit = ModPonders.addScenes(rawHelper)
}
