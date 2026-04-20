package me.mochibit.createharmonics

import me.mochibit.createharmonics.ponder.ModPonderPlugin
import net.createmod.ponder.foundation.PonderIndex
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod

@Mod(CreateHarmonicsMod.MOD_ID, dist = [Dist.CLIENT])
class NeoforgeModClientEntryPoint(
    val modEventBus: IEventBus,
    val container: ModContainer,
) {
    companion object {
        @JvmStatic
        lateinit var instance: NeoforgeModClientEntryPoint
            private set
    }

    init {
        instance = this
        initialize()
    }

    private fun initialize() {
        PonderIndex.addPlugin(ModPonderPlugin())
        CreateHarmonicsClientMod.setup()
    }
}
