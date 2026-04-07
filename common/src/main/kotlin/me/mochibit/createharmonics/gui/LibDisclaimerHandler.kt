package me.mochibit.createharmonics.gui

import me.mochibit.createharmonics.audio.bin.FFMPEGProvider
import me.mochibit.createharmonics.audio.bin.YTDLProvider
import me.mochibit.createharmonics.config.ModConfigs
import me.mochibit.createharmonics.foundation.eventbus.EventBus
import me.mochibit.createharmonics.foundation.eventbus.ProxyEvent
import me.mochibit.createharmonics.foundation.eventbus.TickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen

object LibDisclaimerHandler : CommonGuiEventHandler {
    private var hasShownDisclaimer = false
    private var hasChecked = false

    override fun setupEvents() {
        EventBus.onMcMain<TickEvents.ClientTickEvent> { event ->
            if (event.phase != TickEvents.Phase.END) return@onMcMain
            if (hasChecked) return@onMcMain

            val minecraft = Minecraft.getInstance()
            val currentScreen = minecraft.screen

            if (currentScreen is TitleScreen) {
                hasChecked = true

                // Only show once per game session
                if (hasShownDisclaimer) {
                    return@onMcMain
                }

                // Check if user has disabled the disclaimer
                if (ModConfigs.client.neverShowLibraryDisclaimer.get()) {
                    hasShownDisclaimer = true
                    return@onMcMain
                }

                // Check if libraries are already installed
                val ytdlInstalled = YTDLProvider.isAvailable()
                val ffmpegInstalled = FFMPEGProvider.isAvailable()

                if (ytdlInstalled && ffmpegInstalled) {
                    hasShownDisclaimer = true
                    return@onMcMain
                }

                hasShownDisclaimer = true

                minecraft.setScreen(LibraryDisclaimerScreen(currentScreen))
            }
        }
    }
}
