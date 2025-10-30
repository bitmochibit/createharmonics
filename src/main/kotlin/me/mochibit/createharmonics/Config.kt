package me.mochibit.createharmonics

import net.minecraftforge.common.ForgeConfigSpec
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod.EventBusSubscriber
import net.minecraftforge.fml.event.config.ModConfigEvent
@EventBusSubscriber(modid = CreateHarmonicsMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
object Config {
    private val BUILDER = ForgeConfigSpec.Builder()
    
    enum class RecordType {
        STONE, GOLD, EMERALD, DIAMOND, NETHERITE, ETERNAL
    }

    /**
     * Default record variants with their durability values.
     * null durability = unbreakable record
     */
    val recordVariants: List<Pair<RecordType, Int?>> = listOf(
        RecordType.STONE to 2,
        RecordType.GOLD to 4,
        RecordType.EMERALD to 8,
        RecordType.DIAMOND to 32,
        RecordType.NETHERITE to 64,
        RecordType.ETERNAL to null
    )
    
    private val recordDurabilities: ForgeConfigSpec.ConfigValue<List<String>>

    init {
        BUILDER.push("ethereal_records")
        recordDurabilities = BUILDER
            .comment(
                "Customize record durability. Format: RECORD_TYPE=uses",
                "Use 0 or 'null' for unbreakable records.",
                "Examples: STONE=5, ETERNAL=0"
            )
            .defineList(
                "durabilities",
                recordVariants.map { "${it.first.name}=${it.second ?: 0}" }
            ) { it is String && it.matches(Regex("^[A-Z_]+=(null|[0-9]+)$")) }
        BUILDER.pop()
    }

    /**
     * Get the configured durability for a record type.
     * Returns null if unbreakable, or the number of uses.
     */
    fun getRecordDurability(recordType: RecordType): Int? {
        val configMap = recordDurabilities.get().mapNotNull { entry ->
            val parts = entry.split("=")
            try {
                RecordType.valueOf(parts[0]) to parts[1]
            } catch (e: Exception) {
                null
            }
        }.toMap()

        val value = configMap[recordType] ?: return recordVariants.find { it.first == recordType }?.second
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