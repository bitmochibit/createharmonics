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
            val defaultDurability =
                when (type) {
                    RecordType.STONE -> 20
                    RecordType.GOLD -> 1
                    RecordType.EMERALD -> 800
                    RecordType.DIAMOND -> 1500
                    RecordType.NETHERITE -> 2000
                    RecordType.BRASS -> 250
                }
            val configName = "maxUses_${type.name.lowercase()}"
            val c =
                i(
                    defaultDurability,
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
        val value = configInt.get()
        return if (value == 0) null else value
    }

    override fun getName(): String = "common"
}
