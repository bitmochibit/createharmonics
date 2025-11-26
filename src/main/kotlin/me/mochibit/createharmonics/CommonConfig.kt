package me.mochibit.createharmonics

import me.mochibit.createharmonics.content.item.record.RecordType
import net.createmod.catnip.config.ConfigBase
import net.minecraftforge.common.ForgeConfigSpec
import net.minecraftforge.fml.common.Mod.EventBusSubscriber

@EventBusSubscriber(modid = CreateHarmonicsMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
object CommonConfig : ConfigBase() {

    lateinit var minPitch: ConfigFloat
        private set

    lateinit var maxPitch: ConfigFloat
        private set

    lateinit var playbackBufferSeconds: ConfigFloat
        private set

    lateinit var acceptedHttpDomains: CValue<String, ForgeConfigSpec.ConfigValue<String>>
        private set

    private val recordDurabilities: MutableMap<RecordType, ConfigInt> = mutableMapOf()

    override fun registerAll(builder: ForgeConfigSpec.Builder) {
        audioSourceGroup(builder)
        recordGroup()
        super.registerAll(builder)
    }

    fun audioSourceGroup(builder: ForgeConfigSpec.Builder) {
        group(1, "audio_sources", "Configuration for audio sources and playback")
        minPitch = f(0.5f, 0.1f, 1.0f, "minPitch")
        maxPitch = f(2.0f, 1.0f, 4.0f, "maxPitch")
        playbackBufferSeconds = f(0.05f, 0.01f, 30.0f, "playbackBufferSeconds")
        acceptedHttpDomains = CValue(
            "acceptedHttpDomains",
            {
                builder.define("acceptedHttpDomains", "youtube.com, youtu.be, soundcloud.com")
            },
            "List of accepted HTTP domains for audio sources, separated by commas."
        )
    }

    fun recordGroup() {
        group(1, "records", "Configuration for ethereal records")
        for (type in RecordType.entries) {
            val configName = "maxUses_${type.name.lowercase()}"
            val c = i(60, 0, 1000, configName, "Maximum uses for ${type.name} record. Set to 0 for unbreakable.")
            recordDurabilities[type] = c
        }
    }

    fun getRecordDurability(recordType: RecordType): Int? {
        val configInt = recordDurabilities[recordType] ?: return null
        val value = configInt.get()
        return if (value == 0) null else value
    }

    fun getAcceptedHttpDomains(): List<String> {
        val raw = acceptedHttpDomains.get()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    override fun getName(): String {
        return "common"
    }
}