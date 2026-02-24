package me.mochibit.createharmonics.foundation.ponder

import com.simibubi.create.foundation.ponder.PonderWorldBlockEntityFix
import me.mochibit.createharmonics.ForgeCreateHarmonicsMod
import me.mochibit.createharmonics.registry.ModPonders
import net.createmod.ponder.api.level.PonderLevel
import net.createmod.ponder.api.registration.PonderPlugin
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper
import net.minecraft.resources.ResourceLocation

class ModPonderPlugin : PonderPlugin {
    override fun getModId(): String = ForgeCreateHarmonicsMod.MOD_ID

    override fun registerScenes(helper: PonderSceneRegistrationHelper<ResourceLocation>) {
        ModPonders.addScenes(helper)
    }

    override fun registerTags(helper: PonderTagRegistrationHelper<ResourceLocation>) {
        ModPonders.addTags(helper)
    }

    override fun onPonderLevelRestore(ponderLevel: PonderLevel) {
        PonderWorldBlockEntityFix.fixControllerBlockEntities(ponderLevel)
    }
}
