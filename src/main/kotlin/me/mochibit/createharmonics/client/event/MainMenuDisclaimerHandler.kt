package me.mochibit.createharmonics.client.event

import me.mochibit.createharmonics.audio.bin.FFMPEGProvider
import me.mochibit.createharmonics.audio.bin.YTDLProvider
import me.mochibit.createharmonics.client.gui.LibraryDisclaimerScreen
import me.mochibit.createharmonics.registry.ModConfigurations
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
object MainMenuDisclaimerHandler {
    private var hasShownDisclaimer = false
    private var hasChecked = false

    @JvmStatic
    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        if (hasChecked) return

        val minecraft = Minecraft.getInstance()
        val currentScreen = minecraft.screen

        if (currentScreen is TitleScreen) {
            hasChecked = true

            // Only show once per game session
            if (hasShownDisclaimer) {
                return
            }

            // Check if user has disabled the disclaimer
            if (ModConfigurations.client.neverShowLibraryDisclaimer.get()) {
                hasShownDisclaimer = true
                return
            }

            // Check if libraries are already installed
            val ytdlInstalled = YTDLProvider.isAvailable()
            val ffmpegInstalled = FFMPEGProvider.isAvailable()

            if (ytdlInstalled && ffmpegInstalled) {
                hasShownDisclaimer = true
                return
            }

            hasShownDisclaimer = true

            minecraft.setScreen(LibraryDisclaimerScreen(currentScreen))
        }
    }
}
