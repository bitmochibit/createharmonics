package me.mochibit.createharmonics

import net.createmod.catnip.config.ConfigBase
import net.minecraftforge.common.ForgeConfigSpec
import net.minecraftforge.fml.common.Mod.EventBusSubscriber

@EventBusSubscriber(modid = CreateHarmonicsMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
object ClientConfig : ConfigBase() {
    lateinit var minPitch: ConfigFloat
        private set

    lateinit var maxPitch: ConfigFloat
        private set

    lateinit var playbackBufferSeconds: ConfigFloat
        private set

    lateinit var enableSpeedBasedPitch: ConfigBool
        private set

    lateinit var mainMenuLibButtonRow: ConfigInt
        private set

    lateinit var mainMenuLibButtonOffsetX: ConfigInt
        private set

    lateinit var ingameMenuLibButtonRow: ConfigInt
        private set

    lateinit var ingameMenuLibButtonOffsetX: ConfigInt
        private set

    lateinit var neverShowLibraryDisclaimer: ConfigBool
        private set

    override fun registerAll(builder: ForgeConfigSpec.Builder) {
        menuButtonsGroup(builder)
        audioSourceGroup(builder)
        libraryGroup(builder)
        super.registerAll(builder)
    }

    private fun menuButtonsGroup(builder: ForgeConfigSpec.Builder) {
        group(1, "menu_buttons", "Configuration for menu buttons")

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
                -28,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                "mainMenuLibButtonOffsetX",
                "",
                "Offset the Lib Download menu button in the main menu by this many pixels on the X axis",
                "The sign (-/+) of this value determines what side of the row the button appears on (left/right)",
            )

        ingameMenuLibButtonRow =
            i(
                3,
                0,
                5,
                "ingameMenuLibButtonRow",
                "",
                "Choose the menu row that the Lib Download menu button appears on in the ingame menu",
                "Set to 0 to disable the button altogether",
            )

        ingameMenuLibButtonOffsetX =
            i(
                -28,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                "ingameMenuLibButtonOffsetX",
                "",
                "Offset the Lib Download menu button in the ingame menu by this many pixels on the X axis",
                "The sign (-/+) of this value determines what side of the row the button appears on (left/right)",
            )
    }

    private fun audioSourceGroup(builder: ForgeConfigSpec.Builder) {
        group(1, "audio_sources", "Configuration for audio sources and playback")

        minPitch = f(0.5f, 0.1f, 1.0f, "minPitch", "Minimum pitch for audio playback")
        maxPitch = f(2.0f, 1.0f, 4.0f, "maxPitch", "Maximum pitch for audio playback")
        playbackBufferSeconds =
            f(0.05f, 0.01f, 30.0f, "playbackBufferSeconds", "Buffer time in seconds for audio playback")

        enableSpeedBasedPitch =
            b(
                true,
                "enableSpeedBasedPitch",
                "Enable pitch changes based on contraption speed",
                "When enabled, the pitch of audio will change based on the rotational speed of the contraption",
                "Disable this if you find the pitch changes annoying",
            )
    }

    private fun libraryGroup(builder: ForgeConfigSpec.Builder) {
        group(1, "library", "Configuration for external library management")

        neverShowLibraryDisclaimer =
            b(
                false,
                "neverShowLibraryDisclaimer",
                "Never show library installation disclaimer on startup",
                "When enabled, the library installation prompt will not appear on game startup",
                "You can still access the library installer through the in-game menu button",
            )
    }

    override fun getName(): String = "client"
}
