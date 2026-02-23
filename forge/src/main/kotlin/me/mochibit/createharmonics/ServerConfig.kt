package me.mochibit.createharmonics

import me.mochibit.createharmonics.content.records.RecordType
import net.createmod.catnip.config.ConfigBase
import net.minecraftforge.common.ForgeConfigSpec
import net.minecraftforge.fml.common.Mod.EventBusSubscriber

/**
 * Server-side configuration for CreateHarmonics.
 *
 * These settings are synchronized to clients and can be adjusted by server administrators
 * to control gameplay mechanics and balance.
 */
@EventBusSubscriber(modid = CreateHarmonicsMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
object ServerConfig : ConfigBase() {
    // Stress configuration (nested)
    val modStress = nested(0, { ModStress }, "Mechanical stress impacts and capacities for CreateHarmonics blocks")

    // Record durability configuration
    private val recordDurabilities: MutableMap<RecordType, ConfigInt> = mutableMapOf()

    // Durability constraints
    private const val MIN_DURABILITY = 0
    private const val MAX_DURABILITY = 30000
    private const val UNBREAKABLE = 0

    lateinit var maxJukeboxSoundRange: ConfigInt
        private set

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

    override fun registerAll(builder: ForgeConfigSpec.Builder) {
        recordGroup()
        jukeboxesGroup()
        super.registerAll(builder)
    }

    override fun getName(): String = "server"
}
