package me.mochibit.createharmonics.client.event

import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.audio.binProvider.FFMPEGProvider
import me.mochibit.createharmonics.audio.binProvider.YTDLProvider
import me.mochibit.createharmonics.client.gui.LibraryDisclaimerScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent

object MainMenuDisclaimerHandler {
    
    private var hasShownDisclaimer = false
    private var hasChecked = false
    
    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        if (hasChecked) return
        
        val minecraft = Minecraft.getInstance()
        val currentScreen = minecraft.screen
        
        // Check if we're on the title screen
        if (currentScreen is TitleScreen) {
            Logger.info("TitleScreen detected!")
            hasChecked = true
            
            // Only show once per game session
            if (hasShownDisclaimer) {
                return
            }
            
            // Check if libraries are already installed
            val ytdlInstalled = YTDLProvider.isAvailable()
            val ffmpegInstalled = FFMPEGProvider.isAvailable()
            
            if (ytdlInstalled && ffmpegInstalled) {
                Logger.info("All libraries are installed, skipping disclaimer screen")
                hasShownDisclaimer = true
                return
            }
            
            // Show disclaimer screen if any library is missing
            Logger.info("Showing library disclaimer screen (yt-dlp: $ytdlInstalled, ffmpeg: $ffmpegInstalled)")
            hasShownDisclaimer = true
            
            minecraft.setScreen(LibraryDisclaimerScreen(currentScreen))
        }
    }
}

