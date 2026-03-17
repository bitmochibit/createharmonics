package me.mochibit.createharmonics.gui

import me.mochibit.createharmonics.audio.bin.FFMPEGProvider
import me.mochibit.createharmonics.audio.bin.YTDLProvider
import me.mochibit.createharmonics.foundation.eventbus.EventBus
import me.mochibit.createharmonics.foundation.eventbus.ProxyEvent
import me.mochibit.createharmonics.foundation.services.configService
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen

object LibDisclaimerHandler : CommonGuiEventHandler {
    private var hasShownDisclaimer = false
    private var hasChecked = false

    override fun setupEvents() {
        EventBus.on<ProxyEvent.TickEvent.ClientTickEventProxy> { event ->
            if (event.phase != ProxyEvent.TickEvent.Phase.END) return@on
            if (hasChecked) return@on

            val minecraft = Minecraft.getInstance()
            val currentScreen = minecraft.screen

            if (currentScreen is TitleScreen) {
                hasChecked = true

                // Only show once per game session
                if (hasShownDisclaimer) {
                    return@on
                }

                // Check if user has disabled the disclaimer
                if (configService.getNeverShowLibraryDisclaimer()) {
                    hasShownDisclaimer = true
                    return@on
                }

                // Check if libraries are already installed
                val ytdlInstalled = YTDLProvider.isAvailable()
                val ffmpegInstalled = FFMPEGProvider.isAvailable()

                if (ytdlInstalled && ffmpegInstalled) {
                    hasShownDisclaimer = true
                    return@on
                }

                hasShownDisclaimer = true

                minecraft.setScreen(LibraryDisclaimerScreen(currentScreen))
            }
        }
    }
}
