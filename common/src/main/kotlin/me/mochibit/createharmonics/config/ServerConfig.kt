package me.mochibit.createharmonics.config

import me.mochibit.createharmonics.config.entries.ByteSizeAnnotation
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.foundation.services.platformService
import net.createmod.catnip.config.ConfigBase
import net.neoforged.neoforge.common.ModConfigSpec

object ServerConfig : ConfigBase() {

    lateinit var audioServerIp: CValue<String, ModConfigSpec.ConfigValue<String>>
        private set
    lateinit var audioServerPort: ConfigInt
        private set
    lateinit var audioStorageRoot: CValue<String, ModConfigSpec.ConfigValue<String>>
        private set
    lateinit var maxFilesPerPlayer: ConfigInt
        private set

    lateinit var maxFileSizeBytes: CValue<Long, ModConfigSpec.LongValue>
        private set

    val modStress =
        nested(0, { ModStressConfig }, "Mechanical stress impacts and capacities for CreateHarmonics blocks")

    private val recordDurabilities: MutableMap<RecordType, ConfigInt> = mutableMapOf()

    private const val MIN_DURABILITY = 0
    private const val MAX_DURABILITY = 30000
    private const val UNBREAKABLE = 0

    lateinit var maxJukeboxSoundRange: ConfigInt
        private set

    lateinit var minPitch: ConfigFloat
        private set

    lateinit var maxPitch: ConfigFloat
        private set

    private fun byteSize(
        name: String,
        defaultBytes: Long,
        minBytes: Long,
        maxBytes: Long,
        vararg comment: String,
    ): CValue<Long, ModConfigSpec.LongValue> =
        CValue<Long, ModConfigSpec.LongValue>(
            name,
            { builder -> builder.defineInRange(name, defaultBytes, minBytes, maxBytes) },
            *comment,
            ByteSizeAnnotation.LINE,
        )

    private fun audioServerGroup() {
        group(1, "Audio server", "Configure the audio file hoster")
        audioServerIp =
            CValue<String, ModConfigSpec.ConfigValue<String>>(
                "audioServerIp",
                { builder -> builder.define("audioServerIp", "") },
                "Host IP for the audio server, EMPTY if the audio server lives in the same host; only specify an IP if you're doing something with proxies.",
            )

        audioServerPort = i(25570, 0, 65535, "Server port")

        audioStorageRoot = CValue<String, ModConfigSpec.ConfigValue<String>>(
            "audioStorageRoot",
            { builder -> builder.define("audioStorageRoot", "createharmonics/audio/uploaded") },
            "Root directory for uploaded audio files. Relative paths are resolved against the game/server directory; absolute paths are used as they are.",
        )

        maxFilesPerPlayer = i(20, 1, 9999, "Maximum files (count) that one player can upload")

        maxFileSizeBytes = byteSize(
            "maxFileSizeBytes",
            10L shl 20,
            1L shl 10,
            10L shl 30,
            "Maximum size per file that one player can upload. Default is 10 megabytes",
        )
    }

    private fun recordGroup() {
        group(1, "records", "Configuration for ethereal records and their durability")
        for (type in RecordType.entries) {
            val configName = "maxUses_${type.name.lowercase()}"
            recordDurabilities[type] = i(
                type.uses,
                MIN_DURABILITY,
                MAX_DURABILITY,
                configName,
                "Maximum uses for ${type.name} record before it breaks. Set to $UNBREAKABLE for unbreakable.",
            )
        }
    }

    private fun jukeboxesGroup() {
        group(1, "jukeboxes", "Configuration for the Andesite Jukebox block")
        maxJukeboxSoundRange = i(
            32, 5, 4095,
            "maxSoundRange",
            "Maximum sound range (in blocks) for the Andesite Jukebox.",
        )

        minPitch = f(0.5f, 0.1f, 1.0f, "minPitch", "Minimum pitch for audio playback")

        maxPitch = f(2.0f, 1.0f, 4.0f, "maxPitch", "Maximum pitch for audio playback")
    }

    fun getRecordDurability(recordType: RecordType): Int? {
        val configInt = recordDurabilities[recordType] ?: return null
        return try {
            configInt.get()
        } catch (_: IllegalStateException) {
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