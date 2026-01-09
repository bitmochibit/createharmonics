package me.mochibit.createharmonics

import net.createmod.catnip.config.ConfigBase
import net.minecraftforge.common.ForgeConfigSpec
import net.minecraftforge.fml.common.Mod.EventBusSubscriber

@EventBusSubscriber(modid = CreateHarmonicsMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
object ServerConfig : ConfigBase() {
    // Server config is for settings that only the server needs to know about
    // and should be synced to clients when they connect
    // Currently empty, but available for future server-side only configurations

    override fun registerAll(builder: ForgeConfigSpec.Builder) {
        super.registerAll(builder)
    }

    override fun getName(): String = "server"
}
