package me.mochibit.createharmonics

import me.mochibit.createharmonics.content.records.RecordType
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

    private val recordDurabilities: MutableMap<RecordType, ConfigInt> = mutableMapOf()

    lateinit var mainMenuLibButtonRow: ConfigInt
    lateinit var mainMenuLibButtonOffsetX: ConfigInt

    lateinit var ingameMenuLibButtonRow: ConfigInt
    lateinit var ingameMenuLibButtonOffsetX: ConfigInt

    override fun registerAll(builder: ForgeConfigSpec.Builder) {
        mainMenuLibButtonRow =
            i(
                3,
                0,
                4,
                "mainMenuLibButtonRow",
                "",
                "Choose the menu row that the Lib Download menu button appears on in the main menu",
                "Set to 0 to disable the button altogether",
            )

        mainMenuLibButtonOffsetX =
            i(
                -4,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                "mainMenuLibButtonOffsetX",
                "",
                "Offset the Lib Download menu button in the main menu by this many pixels on the X axis",
                "The sign (-/+) of this value determines what side of the row the button appears on (left/right)",
            )

        ingameMenuLibButtonRow =
            i(
                4,
                0,
                5,
                "ingameMenuLibButtonRow",
                "",
                "Choose the menu row that the Lib Download menu button appears on in the main menu",
                "Set to 0 to disable the button altogether",
            )

        ingameMenuLibButtonOffsetX =
            i(
                -4,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                "ingameMenuLibButtonOffsetX",
                "",
                "Offset the Lib Download menu button in the main menu by this many pixels on the X axis",
                "The sign (-/+) of this value determines what side of the row the button appears on (left/right)",
            )

        audioSourceGroup(builder)
        recordGroup()
        super.registerAll(builder)
    }

    fun audioSourceGroup(builder: ForgeConfigSpec.Builder) {
        group(1, "audio_sources", "Configuration for audio sources and playback")
        minPitch = f(0.5f, 0.1f, 1.0f, "minPitch")
        maxPitch = f(2.0f, 1.0f, 4.0f, "maxPitch")
        playbackBufferSeconds = f(0.05f, 0.01f, 30.0f, "playbackBufferSeconds")
    }

    fun recordGroup() {
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
