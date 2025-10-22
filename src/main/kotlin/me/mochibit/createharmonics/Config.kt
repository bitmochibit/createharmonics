package me.mochibit.createharmonics

import net.minecraftforge.common.ForgeConfigSpec
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod.EventBusSubscriber
import net.minecraftforge.fml.event.config.ModConfigEvent

@EventBusSubscriber(modid = CreateHarmonicsMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
object Config {
    private val BUILDER = ForgeConfigSpec.Builder()

    enum class DiscType {
        STONE, GOLD, EMERALD, DIAMOND, NETHERITE, ETERNAL
    }

    // Defaults used for registration
    val variants: List<Pair<DiscType, Int?>> = listOf(
        DiscType.STONE to 2,
        DiscType.GOLD to 4,
        DiscType.EMERALD to 8,
        DiscType.DIAMOND to 32,
        DiscType.NETHERITE to 64,
        DiscType.ETERNAL to null
    )

    private val discs: ForgeConfigSpec.ConfigValue<List<String>>

    init {
        BUILDER.push("ethereal_disc_durabilities")
        discs = BUILDER
            .comment("List of disc durability. Use durability 0 for unbreakable.")
            .defineList(
                "discs",
                variants.map { "${it.first}=${it.second}" }
            ) { it is String && (it as String).matches(Regex("^[A-Z_]+=(null|[0-9]+)$")) }
        BUILDER.pop()
    }

    fun getConfiguredVariants(): List<Pair<DiscType, Int?>> {
        return discs.get().mapNotNull { entry ->
            val (name, value) = entry.split("=")
            try {
                val type = DiscType.valueOf(name)
                type to value.toIntOrNull()
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
    // Audio buffering and pitch constraints
    val MIN_PITCH: ForgeConfigSpec.DoubleValue = BUILDER
        .comment("Minimum pitch value for audio playback (default: 0.5 = half speed)")
        .defineInRange("minPitch", 0.5, 0.1, 1.0)

    val MAX_PITCH: ForgeConfigSpec.DoubleValue = BUILDER
        .comment("Maximum pitch value for audio playback (default: 2.0 = double speed)")
        .defineInRange("maxPitch", 2.0, 1.0, 4.0)

    val PLAYBACK_BUFFER_SECONDS: ForgeConfigSpec.DoubleValue = BUILDER
        .comment("Target buffer size in seconds of playback time (default: 0.05). Larger values = more stable but higher latency. Reduce for faster pitch response.")
        .defineInRange("playbackBufferSeconds", 0.05, 0.01, 30.0)

    val LIBRARIES_ACCEPTED: ForgeConfigSpec.BooleanValue = BUILDER
        .comment("Whether the user has accepted the download of external libraries (yt-dlp and FFmpeg)")
        .define("librariesAccepted", false)

    val SPEC: ForgeConfigSpec = BUILDER.build()

    @SubscribeEvent
    fun onLoad(event: ModConfigEvent?) {
    }
}