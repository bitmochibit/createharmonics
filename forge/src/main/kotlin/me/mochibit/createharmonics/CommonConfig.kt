package me.mochibit.createharmonics

import net.createmod.catnip.config.ConfigBase
import net.minecraftforge.common.ForgeConfigSpec
import net.minecraftforge.fml.common.Mod.EventBusSubscriber

@EventBusSubscriber(modid = ForgeModEntryPoint.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
object CommonConfig : ConfigBase() {
    override fun registerAll(builder: ForgeConfigSpec.Builder) {
        super.registerAll(builder)
    }

    override fun getName(): String = "common"
}
