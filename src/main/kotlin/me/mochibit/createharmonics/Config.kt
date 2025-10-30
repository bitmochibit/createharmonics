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

    /**
     * Default disc variants with their durability values.
     * null durability = unbreakable disc
     */
    val diskVariants: List<Pair<DiscType, Int?>> = listOf(
        DiscType.STONE to 2,
        DiscType.GOLD to 4,
        DiscType.EMERALD to 8,
        DiscType.DIAMOND to 32,
        DiscType.NETHERITE to 64,
        DiscType.ETERNAL to null
    )
    
    private val discDurabilities: ForgeConfigSpec.ConfigValue<List<String>>

    init {
        BUILDER.push("ethereal_discs")
        discDurabilities = BUILDER
            .comment(
                "Customize disc durability. Format: DISC_TYPE=uses",
                "Use 0 or 'null' for unbreakable discs.",
                "Examples: STONE=5, ETERNAL=0"
            )
            .defineList(
                "durabilities",
                diskVariants.map { "${it.first.name}=${it.second ?: 0}" }
            ) { it is String && it.matches(Regex("^[A-Z_]+=(null|[0-9]+)$")) }
        BUILDER.pop()
    }

    /**
     * Get the configured durability for a disc type.
     * Returns null if unbreakable, or the number of uses.
     */
    fun getDiscDurability(discType: DiscType): Int? {
        val configMap = discDurabilities.get().mapNotNull { entry ->
            val parts = entry.split("=")
            try {
                DiscType.valueOf(parts[0]) to parts[1]
            } catch (e: Exception) {
                null
            }
        }.toMap()

        val value = configMap[discType] ?: return diskVariants.find { it.first == discType }?.second
        return if (value == "null" || value == "0") null else value.toIntOrNull()
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

    val ACCEPTED_HTTP_DOMAINS: ForgeConfigSpec.ConfigValue<List<String>> = BUILDER
        .comment(
            "List of accepted HTTP domains for streaming audio.",
            "Only domains in this list will be allowed for audio playback.",
            "Examples: example.com, anotherdomain.org"
        )
        .defineList(
            "acceptedHttpDomains",
            listOf("youtube.com", "soundcloud.com")
        ) { it is String && it.matches(Regex("^[a-zA-Z0-9.-]+$")) }

    val SPEC: ForgeConfigSpec = BUILDER.build()

    @SubscribeEvent
    fun onLoad(event: ModConfigEvent?) {
    }
}