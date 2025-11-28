package me.mochibit.createharmonics.client.gui

import kotlinx.coroutines.Dispatchers
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.client.audio.binProvider.FFMPEGProvider
import me.mochibit.createharmonics.client.audio.binProvider.YTDLProvider
import me.mochibit.createharmonics.coroutine.launchModCoroutine
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.util.FormattedCharSequence

class LibraryDisclaimerScreen(private val parent: Screen?) : Screen(Component.literal("Library Download Required")) {

    private var acceptButton: Button? = null
    private var declineButton: Button? = null
    private var shutdownCountdown = 5
    private var declined = false
    private var tickCounter = 0
    private var downloading = false
    private var ytdlpStatus = "pending"
    private var ffmpegStatus = "pending"
    private var ytdlpProgress = 0.0f
    private var ffmpegProgress = 0.0f
    private var ytdlpSpeed = ""
    private var ffmpegSpeed = ""

    private fun getDisclaimerText(): List<Component> = listOf(
        Component.literal("Create: Harmonics - External Libraries Required")
            .withStyle(ChatFormatting.BOLD, ChatFormatting.UNDERLINE),
        Component.empty(),
        Component.literal("This mod requires two external libraries to function:"),
        Component.empty(),
        Component.literal("1. yt-dlp").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" - A command-line program to download videos from")),
        Component.literal("   YouTube and other video platforms"),
        Component.literal("   License: The Unlicense (Public Domain)"),
        Component.literal("   Source: ").append(
            Component.literal("https://github.com/yt-dlp/yt-dlp").withStyle(ChatFormatting.AQUA)
        ),
        Component.empty(),
        Component.literal("2. FFmpeg").withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" - A complete, cross-platform solution to record,")),
        Component.literal("   convert and stream audio and video"),
        Component.literal("   License: GNU LGPL 2.1+ / GNU GPL 2+"),
        Component.literal("   Source: ").append(
            Component.literal("https://ffmpeg.org/").withStyle(ChatFormatting.AQUA)
        ),
        Component.empty(),
        Component.literal("These libraries will be downloaded automatically when you"),
        Component.literal("click 'Accept' below. They are essential for the mod to work."),
        Component.empty(),
        Component.literal("If you decline, you must remove this mod from your game.")
            .withStyle(ChatFormatting.RED)
    )

    override fun init() {
        super.init()

        val buttonWidth = 200
        val buttonHeight = 20
        val centerX = width / 2
        val buttonY = height - 40

        if (!declined) {
            acceptButton = Button.builder(Component.literal("Accept & Download")) { _ ->
                onAccept()
            }
                .bounds(centerX - buttonWidth - 10, buttonY, buttonWidth, buttonHeight)
                .build()

            declineButton = Button.builder(Component.literal("Decline")) { _ ->
                onDecline()
            }
                .bounds(centerX + 10, buttonY, buttonWidth, buttonHeight)
                .build()

            addRenderableWidget(acceptButton!!)
            addRenderableWidget(declineButton!!)
        }
    }

    private fun onAccept() {
        acceptButton?.active = false
        declineButton?.active = false
        downloading = true


        Logger.info("User accepted library download, starting installation...")

        // Download libraries in background
        launchModCoroutine(Dispatchers.IO) {
            try {
                Logger.info("Installing yt-dlp...")
                ytdlpStatus = "downloading"
                val ytdlSuccess = YTDLProvider.install { status, progress, speed ->
                    ytdlpStatus = status
                    ytdlpProgress = progress
                    ytdlpSpeed = speed
                }

                Logger.info("Installing FFmpeg...")
                ffmpegStatus = "downloading"
                val ffmpegSuccess = FFMPEGProvider.install { status, progress, speed ->
                    ffmpegStatus = status
                    ffmpegProgress = progress
                    ffmpegSpeed = speed
                }

                if (ytdlSuccess && ffmpegSuccess) {
                    Logger.info("All libraries installed successfully!")
                } else {
                    Logger.err("Failed to install some libraries: yt-dlp=$ytdlSuccess, ffmpeg=$ffmpegSuccess")
                }

                // Return to main menu
                Minecraft.getInstance().execute {
                    minecraft?.setScreen(parent)
                }
            } catch (e: Exception) {
                Logger.err("Error during library installation: ${e.message}")
                e.printStackTrace()
                ytdlpStatus = "failed"
                ffmpegStatus = "failed"
            }
        }
    }

    private fun onDecline() {
        declined = true
        removeWidget(acceptButton!!)
        removeWidget(declineButton!!)
        acceptButton = null
        declineButton = null
    }

    override fun tick() {
        super.tick()

        if (declined) {
            tickCounter++
            if (tickCounter >= 20) { // 20 ticks = 1 second
                tickCounter = 0
                shutdownCountdown--

                if (shutdownCountdown <= 0) {
                    Logger.info("User declined library download, shutting down game...")
                    minecraft?.stop()
                }
            }
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics)

        val centerX = width / 2

        // Only render disclaimer text if not downloading and not declined
        if (!downloading && !declined) {
            var yPos = 20

            // Calculate available space for disclaimer text
            val buttonAreaHeight = 60
            val maxTextHeight = height - buttonAreaHeight - 40

            // Render disclaimer text
            val disclaimerComponents = getDisclaimerText()
            for (component in disclaimerComponents) {
                val sequences = font.split(component, width - 60)

                for (sequence in sequences) {
                    if (yPos + 12 <= maxTextHeight) {
                        drawCenteredString(guiGraphics, font, sequence, centerX, yPos, 0xFFFFFF)
                    }
                    yPos += 12
                }
            }
        }

        // Render download status or decline message
        if (downloading) {
            val statusY = height - 140

            drawCenteredString(
                guiGraphics,
                font,
                Component.literal("Downloading libraries, please wait...")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                centerX,
                statusY,
                0xFFFFFF
            )

            // Show yt-dlp status with progress bar
            val ytdlpY = statusY + 25
            val ytdlpStatusComponent = Component.literal("yt-dlp: ")
                .withStyle(ChatFormatting.YELLOW)
                .append(getStatusComponent(ytdlpStatus))
            drawCenteredString(
                guiGraphics,
                font,
                ytdlpStatusComponent,
                centerX,
                ytdlpY,
                0xFFFFFF
            )

            // Draw yt-dlp progress bar
            if (ytdlpStatus == "downloading" && ytdlpProgress > 0) {
                drawProgressBar(guiGraphics, centerX, ytdlpY + 15, ytdlpProgress, ytdlpSpeed)
            }

            // Show FFmpeg status with progress bar
            val ffmpegY = ytdlpY + 40
            val ffmpegStatusComponent = Component.literal("FFmpeg: ")
                .withStyle(ChatFormatting.YELLOW)
                .append(getStatusComponent(ffmpegStatus))
            drawCenteredString(
                guiGraphics,
                font,
                ffmpegStatusComponent,
                centerX,
                ffmpegY,
                0xFFFFFF
            )

            // Draw FFmpeg progress bar
            if (ffmpegStatus == "downloading" && ffmpegProgress > 0) {
                drawProgressBar(guiGraphics, centerX, ffmpegY + 15, ffmpegProgress, ffmpegSpeed)
            }
        } else if (declined) {
            val declineY = height - 110

            drawCenteredString(
                guiGraphics,
                font,
                Component.literal("Not accepted. You will need to remove the mod.")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                centerX,
                declineY,
                0xFFFFFF
            )

            drawCenteredString(
                guiGraphics,
                font,
                Component.literal("Shutting down in $shutdownCountdown seconds...")
                    .withStyle(ChatFormatting.YELLOW),
                centerX,
                declineY + 20,
                0xFFFFFF
            )
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun getStatusComponent(status: String): MutableComponent {
        return when (status) {
            "pending" -> Component.literal("Waiting...").withStyle(ChatFormatting.GRAY)
            "downloading" -> Component.literal("Downloading...").withStyle(ChatFormatting.AQUA)
            "extracting" -> Component.literal("Extracting...").withStyle(ChatFormatting.BLUE)
            "completed" -> Component.literal("✓ Completed").withStyle(ChatFormatting.GREEN)
            "already_installed" -> Component.literal("✓ Already Installed").withStyle(ChatFormatting.GREEN)
            "failed" -> Component.literal("✗ Failed").withStyle(ChatFormatting.RED)
            else -> Component.literal(status).withStyle(ChatFormatting.WHITE)
        }
    }

    private fun drawProgressBar(guiGraphics: GuiGraphics, centerX: Int, y: Int, progress: Float, speedText: String) {
        val barWidth = 200
        val barHeight = 10
        val barX = centerX - barWidth / 2

        // Draw background (dark gray)
        guiGraphics.fill(barX, y, barX + barWidth, y + barHeight, 0xFF3F3F3F.toInt())

        // Draw progress (green)
        val progressWidth = (barWidth * progress.coerceIn(0.0f, 1.0f)).toInt()
        if (progressWidth > 0) {
            guiGraphics.fill(barX, y, barX + progressWidth, y + barHeight, 0xFF00AA00.toInt())
        }

        // Draw border (white)
        guiGraphics.renderOutline(barX, y, barWidth, barHeight, 0xFFFFFFFF.toInt())

        // Draw percentage text centered in the bar
        val percentText = "${(progress * 100).toInt()}%"
        guiGraphics.drawString(font, percentText, barX + barWidth / 2 - font.width(percentText) / 2, y + 1, 0xFFFFFF)

        // Draw speed text to the right of the bar
        if (speedText.isNotEmpty()) {
            guiGraphics.drawString(font, speedText, barX + barWidth + 10, y + 1, 0xAAFFAA)
        }
    }

    private fun drawCenteredString(guiGraphics: GuiGraphics, font: net.minecraft.client.gui.Font,
                                   text: FormattedCharSequence, x: Int, y: Int, color: Int) {
        guiGraphics.drawString(font, text, x - font.width(text) / 2, y, color)
    }

    private fun drawCenteredString(guiGraphics: GuiGraphics, font: net.minecraft.client.gui.Font,
                                   text: Component, x: Int, y: Int, color: Int) {
        drawCenteredString(guiGraphics, font, text.visualOrderText, x, y, color)
    }

    override fun isPauseScreen(): Boolean = true

    override fun shouldCloseOnEsc(): Boolean = false
}

