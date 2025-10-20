package me.mochibit.createharmonics

import net.minecraftforge.common.ForgeConfigSpec
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod.EventBusSubscriber
import net.minecraftforge.fml.event.config.ModConfigEvent

@EventBusSubscriber(modid = CreateHarmonicsMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
object Config {
    private val BUILDER = ForgeConfigSpec.Builder()

    // Audio buffering and pitch constraints
    val MIN_PITCH: ForgeConfigSpec.DoubleValue = BUILDER
        .comment("Minimum pitch value for audio playback (default: 0.5 = half speed)")
        .defineInRange("minPitch", 0.5, 0.1, 1.0)

    val MAX_PITCH: ForgeConfigSpec.DoubleValue = BUILDER
        .comment("Maximum pitch value for audio playback (default: 2.0 = double speed)")
        .defineInRange("maxPitch", 2.0, 1.0, 4.0)

    val PLAYBACK_BUFFER_SECONDS: ForgeConfigSpec.DoubleValue = BUILDER
        .comment("Target buffer size in seconds of playback time (default: 5.0). Larger values = more stable but higher memory usage")
        .defineInRange("playbackBufferSeconds", 5.0, 1.0, 30.0)

    val SPEC: ForgeConfigSpec = BUILDER.build()

    @SubscribeEvent
    fun onLoad(event: ModConfigEvent?) {
    }
}