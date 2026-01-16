package me.mochibit.createharmonics

import me.mochibit.createharmonics.content.records.RecordType
import net.createmod.catnip.config.ConfigBase
import net.minecraftforge.common.ForgeConfigSpec
import net.minecraftforge.fml.common.Mod.EventBusSubscriber

@EventBusSubscriber(modid = CreateHarmonicsMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
object CommonConfig : ConfigBase() {
    // Common config is used for settings that need to be synchronized between client and server
    // Record durabilities are here because they're accessed during item initialization on both sides

    private val recordDurabilities: MutableMap<RecordType, ConfigInt> = mutableMapOf()

    override fun registerAll(builder: ForgeConfigSpec.Builder) {
        recordGroup()
        super.registerAll(builder)
    }

    private fun recordGroup() {
        group(1, "records", "Configuration for ethereal records")
        for (type in RecordType.entries) {
            val configName = "maxUses_${type.name.lowercase()}"
            val c =
                i(
                    type.properties.defaultDurability,
                    0,
                    30000,
                    configName,
                    "Maximum uses for ${type.name} record. Set to 0 for unbreakable.",
                )
            recordDurabilities[type] = c
        }
    }

    fun getRecordDurability(recordType: RecordType): Int? {
        val configInt = recordDurabilities[recordType] ?: return null
        return try {
            configInt.get()
        } catch (_: IllegalStateException) {
            // Config not loaded (e.g., during data generation)
            null
        }
    }

    override fun getName(): String = "common"
}
