package me.mochibit.createharmonics.client.gui

import kotlinx.coroutines.Dispatchers
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.audio.binProvider.FFMPEGProvider
import me.mochibit.createharmonics.audio.binProvider.YTDLProvider
import me.mochibit.createharmonics.coroutine.launchModCoroutine
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

// TODO Asynchronous library download, which can run in background and when ready it can be used
class LibraryDisclaimerScreen(private val parent: Screen?) : Screen(Component.literal("Library Setup")) {

    private data class LibInfo(val name: String, val desc: String, val url: String)

    private val libraries = listOf(
        LibInfo("yt-dlp", "Downloads audio from YouTube & platforms.", "https://github.com/yt-dlp/yt-dlp"),
        LibInfo("FFmpeg", "Processes and converts audio streams.", "https://ffmpeg.org/")
    )

    // State
    private enum class State { DISCLAIMER, DOWNLOADING, SKIPPED }

    private var currentState = State.DISCLAIMER

    // Download Metrics
    private var ytdlpStatus = "Pending..."
    private var ffmpegStatus = "Pending..."
    private var ytdlpProgress = 0.0f
    private var ffmpegProgress = 0.0f
    private var ytdlpSpeed = ""
    private var ffmpegSpeed = ""

    // UI Constants
    private val cardColor = 0x40000000 // Semi-transparent black
    private val borderColor = 0xFF555555.toInt()
    private val warningColor = 0x40FF0000 // Red tint

    override fun init() {
        super.init()
        rebuildWidgets()
    }

    override fun rebuildWidgets() {
        clearWidgets()
        val buttonW = 160
        val buttonH = 20
        val centerX = width / 2
        val bottomY = height - 40

        when (currentState) {
            State.DISCLAIMER -> {
                addRenderableWidget(
                    Button.builder(
                        Component.literal("Accept & Install").withStyle(ChatFormatting.GREEN)
                    ) {
                        startDownload()
                    }.bounds(centerX - buttonW - 5, bottomY, buttonW, buttonH).build()
                )

                addRenderableWidget(Button.builder(Component.literal("Skip (Manual Install)")) {
                    currentState = State.SKIPPED
                    rebuildWidgets()
                }.bounds(centerX + 5, bottomY, buttonW, buttonH).build())
            }

            State.SKIPPED -> {
                addRenderableWidget(Button.builder(Component.literal("I Understand, Proceed")) {
                    onClose() // Go to parent
                }.bounds(centerX - (buttonW / 2), bottomY, buttonW, buttonH).build())
            }

            State.DOWNLOADING -> {
                // No buttons, just waiting
            }
        }
    }

    private fun startDownload() {
        currentState = State.DOWNLOADING
        rebuildWidgets() // Remove buttons

        Logger.info("Starting library installation...")

        launchModCoroutine(Dispatchers.IO) {
            try {
                // 1. YT-DLP
                ytdlpStatus = "Downloading..."
                val ytdlSuccess = YTDLProvider.install { status, progress, speed ->
                    ytdlpStatus = formatStatus(status)
                    ytdlpProgress = progress
                    ytdlpSpeed = speed
                }
                ytdlpStatus = if (ytdlSuccess) "Installed" else "Failed"

                // 2. FFMPEG
                ffmpegStatus = "Downloading..."
                val ffmpegSuccess = FFMPEGProvider.install { status, progress, speed ->
                    ffmpegStatus = formatStatus(status)
                    ffmpegProgress = progress
                    ffmpegSpeed = speed
                }
                ffmpegStatus = if (ffmpegSuccess) "Installed" else "Failed"

                // Finish
                if (ytdlSuccess && ffmpegSuccess) {
                    Logger.info("Library installation complete.")
                    Minecraft.getInstance().execute { onClose() }
                } else {
                    // If failed, we arguably should let them retry or skip,
                    // but for now we just let them proceed to menu so they aren't soft-locked.
                    Minecraft.getInstance().execute { onClose() }
                }
            } catch (e: Exception) {
                Logger.err("Installation crashed: ${e.message}")
                e.printStackTrace()
                Minecraft.getInstance().execute {
                    currentState = State.SKIPPED // Fallback to manual
                    rebuildWidgets()
                }
            }
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics)

        // Dark overlay for focus
        guiGraphics.fillGradient(0, 0, width, height, -1072689136, -804253680)

        val centerX = width / 2
        val centerY = height / 2

        // Draw Title
        val titleScale = 1.5f
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(centerX.toDouble(), 30.0, 0.0)
        guiGraphics.pose().scale(titleScale, titleScale, 1f)
        drawCenteredString(guiGraphics, "Create: Harmonics Setup", 0, 0, 0xFFFFFF)
        guiGraphics.pose().popPose()

        when (currentState) {
            State.DISCLAIMER -> renderDisclaimer(guiGraphics, centerX, centerY)
            State.DOWNLOADING -> renderDownloading(guiGraphics, centerX, centerY)
            State.SKIPPED -> renderSkipped(guiGraphics, centerX, centerY)
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun renderDisclaimer(gfx: GuiGraphics, cx: Int, cy: Int) {
        // Draw Info Text
        drawCenteredString(
            gfx,
            "This mod requires external libraries to function.",
            cx,
            60,
            ChatFormatting.GRAY.color ?: 0xAAAAAA
        )

        // Draw Library Cards
        val cardWidth = 280
        val cardHeight = 50
        var currentY = cy - 40

        libraries.forEach { lib ->
            // Card Background
            gfx.fill(cx - cardWidth / 2, currentY, cx + cardWidth / 2, currentY + cardHeight, cardColor)
            gfx.renderOutline(cx - cardWidth / 2, currentY, cardWidth, cardHeight, borderColor)

            // Lib Name
            gfx.drawString(font, lib.name, cx - cardWidth / 2 + 10, currentY + 8, 0xFFAA00, false)

            // Lib Desc
            gfx.drawString(font, lib.desc, cx - cardWidth / 2 + 10, currentY + 22, 0xDDDDDD, false)

            // Lib URL
            gfx.drawString(font, lib.url, cx - cardWidth / 2 + 10, currentY + 34, 0x55FFFF, false)

            currentY += cardHeight + 10
        }
    }

    private fun renderDownloading(gfx: GuiGraphics, cx: Int, cy: Int) {
        drawCenteredString(gfx, "Downloading binaries...", cx, cy - 60, 0xFFFFFF)

        // Render YT-DLP Progress
        renderProgressBar(gfx, cx, cy - 20, "yt-dlp", ytdlpStatus, ytdlpProgress, ytdlpSpeed)

        // Render FFmpeg Progress
        renderProgressBar(gfx, cx, cy + 30, "FFmpeg", ffmpegStatus, ffmpegProgress, ffmpegSpeed)
    }

    private fun renderProgressBar(
        gfx: GuiGraphics,
        cx: Int,
        y: Int,
        label: String,
        status: String,
        progress: Float,
        speed: String
    ) {
        val barW = 200
        val barH = 6
        val barX = cx - barW / 2

        // Label
        gfx.drawString(font, label, barX, y - 10, 0xFFFFFF, false)
        // Status/Speed (Right aligned)
        val stats = if (speed.isNotEmpty()) "$status ($speed)" else status
        gfx.drawString(font, stats, barX + barW - font.width(stats), y - 10, 0xAAAAAA, false)

        // Bar Background
        gfx.fill(barX, y, barX + barW, y + barH, 0xFF333333.toInt())
        // Bar Fill
        val fillW = (barW * progress.coerceIn(0f, 1f)).toInt()
        if (fillW > 0) {
            gfx.fill(barX, y, barX + fillW, y + barH, 0xFF00AA00.toInt()) // Green fill
        }
        // Border
        gfx.renderOutline(barX - 1, y - 1, barW + 2, barH + 2, 0xFF777777.toInt())
    }

    private fun renderSkipped(gfx: GuiGraphics, cx: Int, cy: Int) {
        val boxW = 300
        val boxH = 100
        val topY = cy - 50

        // Warning Box Background
        gfx.fill(cx - boxW / 2, topY, cx + boxW / 2, topY + boxH, warningColor)
        gfx.renderOutline(cx - boxW / 2, topY, boxW, boxH, 0xFFFF5555.toInt())

        // Warning Icon/Text
        val title =
            Component.literal("⚠ Manual Installation Required").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
        drawCenteredString(gfx, title.visualOrderText, cx, topY + 15, 0xFFFFFF)

        val lines = listOf(
            "You have skipped the automatic library download.",
            "The mod will NOT be able to play internet sources",
            "Download yt-dlp and ffmpeg and place them (extracted) in",
            "",
            FFMPEGProvider.directory.toPath().toAbsolutePath().normalize().toString(),
            YTDLProvider.directory.toPath().toAbsolutePath().normalize().toString(),
        )

        var textY = topY + 35
        lines.forEach { line ->
            val color = if (line.contains("/") || line.contains("\\")) 0xFFFF55 else 0xDDDDDD
            drawCenteredString(gfx, line, cx, textY, color)
            textY += 10
        }
    }

    // Helper for simple text centering
    private fun drawCenteredString(gfx: GuiGraphics, text: String, x: Int, y: Int, color: Int) {
        gfx.drawString(font, text, x - font.width(text) / 2, y, color, false)
    }

    // Helper to handle visual order text (components)
    private fun drawCenteredString(
        gfx: GuiGraphics,
        text: net.minecraft.util.FormattedCharSequence,
        x: Int,
        y: Int,
        color: Int
    ) {
        gfx.drawString(font, text, x - font.width(text) / 2, y, color, false)
    }

    private fun formatStatus(raw: String): String {
        return when (raw) {
            "downloading" -> "Downloading"
            "extracting" -> "Extracting"
            "completed" -> "Done"
            else -> raw.replaceFirstChar { it.uppercase() }
        }
    }

    override fun onClose() {
        // Return to the previous screen (Main Menu usually)
        minecraft?.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = true
    override fun shouldCloseOnEsc(): Boolean = currentState != State.DOWNLOADING
}