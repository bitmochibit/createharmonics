package me.mochibit.createharmonics.gui

import me.mochibit.createharmonics.audio.bin.FFMPEGProvider
import me.mochibit.createharmonics.audio.bin.YTDLProvider
import me.mochibit.createharmonics.config.ModConfigs
import me.mochibit.createharmonics.foundation.eventbus.AutoHandler
import me.mochibit.createharmonics.foundation.eventbus.EventBus
import me.mochibit.createharmonics.foundation.eventbus.ModEventHandler
import me.mochibit.createharmonics.foundation.services.EventPhase
import me.mochibit.createharmonics.foundation.services.clientEventService

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen

@AutoHandler
object LibDisclaimerHandler : ModEventHandler {
    private var hasShownDisclaimer = false
    private var hasChecked = false

    override fun setupEvents() {
        clientEventService.onClientTick(tickPhase = EventPhase.END) {
            if (hasChecked) return@onClientTick

            val minecraft = Minecraft.getInstance()
            val currentScreen = minecraft.screen

            if (currentScreen is TitleScreen) {
                hasChecked = true

                // Only show once per game session
                if (hasShownDisclaimer) {
                    return@onClientTick
                }

                // Check if user has disabled the disclaimer
                if (ModConfigs.client.neverShowLibraryDisclaimer.get()) {
                    hasShownDisclaimer = true
                    return@onClientTick
                }

                // Check if libraries are already installed
                val ytdlInstalled = YTDLProvider.isAvailable()
                val ffmpegInstalled = FFMPEGProvider.isAvailable()

                if (ytdlInstalled && ffmpegInstalled) {
                    hasShownDisclaimer = true
                    return@onClientTick
                }

                hasShownDisclaimer = true

                minecraft.setScreen(LibraryDisclaimerScreen(currentScreen))
            }
        }
    }
}
