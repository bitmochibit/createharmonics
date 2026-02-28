package me.mochibit.createharmonics

import me.mochibit.createharmonics.CreateHarmonicsMod.MOD_ID
import net.createmod.catnip.config.ConfigBase
import net.minecraftforge.common.ForgeConfigSpec
import net.minecraftforge.fml.common.Mod.EventBusSubscriber

@EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD)
object CommonConfig : ConfigBase() {
    override fun registerAll(builder: ForgeConfigSpec.Builder) {
        super.registerAll(builder)
    }

    override fun getName(): String = "common"
}
