package me.mochibit.createharmonics.config

import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.foundation.services.platformService
import net.createmod.catnip.config.ConfigBase
import net.neoforged.neoforge.common.ModConfigSpec

/**
 * Server-side configuration for CreateHarmonics.
 *
 * These settings are synchronized to clients and can be adjusted by server administrators
 * to control gameplay mechanics and balance.
 */
object ServerConfig : ConfigBase() {
    // Audio host configuration

    lateinit var audioServerIp: CValue<String, ModConfigSpec.ConfigValue<String>>
        private set
    lateinit var audioServerPort: ConfigInt
        private set

    lateinit var audioStorageRoot: CValue<String, ModConfigSpec.ConfigValue<String>>
        private set

    lateinit var maxFilesPerPlayer: ConfigInt
        private set

    lateinit var maxFileSizeBytes: CValue<Long, ModConfigSpec.ConfigValue<Long>>
        private set


    // Stress configuration (nested)
    val modStress =
        nested(0, { ModStressConfig }, "Mechanical stress impacts and capacities for CreateHarmonics blocks")

    // Record durability configuration
    private val recordDurabilities: MutableMap<RecordType, ConfigInt> = mutableMapOf()

    // Durability constraints
    private const val MIN_DURABILITY = 0
    private const val MAX_DURABILITY = 30000
    private const val UNBREAKABLE = 0

    lateinit var maxJukeboxSoundRange: ConfigInt
        private set


    private fun audioServerGroup() {
        group(1, "Audio server", "Configure the audio file hoster")
        audioServerIp =
            CValue<String, ModConfigSpec.ConfigValue<String>>(
                "audioServerIp",
                { builder ->
                    builder.define("audioServerIp", "0.0.0.0")
                },
                "Host IP for the audio server, generally the default is good to keep",
            )

        audioServerPort = i(
            25570,
            0,
            65565,
            "Server port"
        )

        audioStorageRoot = CValue<String, ModConfigSpec.ConfigValue<String>>(
            "audioStorageRoot",
            { builder ->
                builder.define("audioStorageRoot",
                    platformService.serverRootPath.resolve("createharmonics/audio/uploaded").toString()
                )
            },
            "Root directory in the server that will be used for storing audio files uploaded by players.",
        )

        maxFilesPerPlayer = i(
            20,
            1,
            9999,
            "Maximum files (count) that one player can upload"
        )

        maxFileSizeBytes = CValue<Long, ModConfigSpec.ConfigValue<Long>>(
            "maxFileSizeBytes",
            { builder ->
                builder.defineInRange("maxFileSizeBytes",
                    10*1024*1024L,
                    1L,
                    10*1024*1024*1024L
                )
            },
            "Maximum size per file that one player can upload (bytes). Default is 10 megabytes",
        )

    }

    /**
     * Registers record-related configuration options.
     * Creates a config entry for each record type's maximum uses.
     */
    private fun recordGroup() {
        group(1, "records", "Configuration for ethereal records and their durability")

        for (type in RecordType.entries) {
            val configName = "maxUses_${type.name.lowercase()}"
            val defaultValue = type.properties.defaultDurability

            val c =
                i(
                    defaultValue,
                    MIN_DURABILITY,
                    MAX_DURABILITY,
                    configName,
                    "Maximum uses for ${type.name} record before it breaks. Set to $UNBREAKABLE for unbreakable.",
                )

            recordDurabilities[type] = c
        }
    }

    private fun jukeboxesGroup() {
        group(1, "jukeboxes", "Configuration for the Andesite Jukebox block")

        maxJukeboxSoundRange =
            i(
                32,
                5,
                4095,
                "maxSoundRange",
                "Maximum sound range (in blocks) for the Andesite Jukebox.",
            )
    }

    /**
     * Gets the configured maximum durability for a specific record type.
     * @param recordType The type of record to query
     * @return The maximum number of uses, or null if config is not loaded (e.g., during data generation)
     */
    fun getRecordDurability(recordType: RecordType): Int? {
        val configInt = recordDurabilities[recordType] ?: return null
        return try {
            configInt.get()
        } catch (_: IllegalStateException) {
            // Config not loaded yet (e.g., during data generation)
            null
        }
    }

    override fun registerAll(builder: ModConfigSpec.Builder) {
        audioServerGroup()
        recordGroup()
        jukeboxesGroup()
        super.registerAll(builder)
    }

    override fun getName(): String = "server"
}
