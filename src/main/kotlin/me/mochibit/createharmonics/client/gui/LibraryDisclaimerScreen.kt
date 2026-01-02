package me.mochibit.createharmonics.client.gui

import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.audio.binProvider.BackgroundLibraryInstaller
import me.mochibit.createharmonics.audio.binProvider.FFMPEGProvider
import me.mochibit.createharmonics.audio.binProvider.YTDLProvider
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class LibraryDisclaimerScreen(
    private val parent: Screen?,
) : Screen(Component.literal("Library Setup")) {
    private data class LibInfo(
        val name: String,
        val desc: String,
        val url: String,
    )

    private val libraries =
        listOf(
            LibInfo("yt-dlp", "Downloads audio from YouTube & platforms.", "https://github.com/yt-dlp/yt-dlp"),
            LibInfo("FFmpeg", "Processes and converts audio streams.", "https://ffmpeg.org/"),
        )

    // State
    private enum class State { DISCLAIMER, STATUS, SKIPPED }

    private var currentState = State.DISCLAIMER

    // UI Constants
    private val cardColor = 0x40000000 // Semi-transparent black
    private val borderColor = 0xFF555555.toInt()
    private val successColor = 0x4000FF00 // Green tint
    private val errorColor = 0x40FF0000 // Red tint
    private val installingColor = 0x4000AAFF // Blue tint
    private val warningColor = 0x40FF0000 // Red tint

    override fun init() {
        super.init()
        // Determine initial state based on installation status
        currentState =
            when {
                BackgroundLibraryInstaller.areAllLibrariesInstalled() -> State.STATUS
                BackgroundLibraryInstaller.isInstalling() -> State.STATUS
                else -> State.DISCLAIMER
            }
        rebuildWidgets()
    }

    override fun rebuildWidgets() {
        clearWidgets()
        val buttonW = 160
        val buttonH = 20
        val buttonGap = 10
        val centerX = width / 2
        val bottomY = height - 40

        when (currentState) {
            State.DISCLAIMER -> {
                // Calculate positions for two centered buttons with gap
                val totalWidth = buttonW * 2 + buttonGap
                val leftButtonX = centerX - totalWidth / 2
                val rightButtonX = leftButtonX + buttonW + buttonGap

                addRenderableWidget(
                    Button
                        .builder(
                            Component.literal("Install in Background").withStyle(ChatFormatting.AQUA),
                        ) {
                            startBackgroundInstallation()
                        }.bounds(leftButtonX, bottomY, buttonW, buttonH)
                        .build(),
                )

                addRenderableWidget(
                    Button
                        .builder(Component.literal("Skip (Manual)")) {
                            currentState = State.SKIPPED
                            rebuildWidgets()
                        }.bounds(rightButtonX, bottomY, buttonW, buttonH)
                        .build(),
                )
            }

            State.STATUS -> {
                val allInstalled = BackgroundLibraryInstaller.areAllLibrariesInstalled()
                val isInstalling = BackgroundLibraryInstaller.isInstalling()

                // Show install button if not all installed and not currently installing
                if (!allInstalled && !isInstalling) {
                    val totalWidth = buttonW * 2 + buttonGap
                    val leftButtonX = centerX - totalWidth / 2
                    val rightButtonX = leftButtonX + buttonW + buttonGap

                    addRenderableWidget(
                        Button
                            .builder(
                                Component.literal("Install Missing Libraries").withStyle(ChatFormatting.GREEN),
                            ) {
                                startBackgroundInstallation()
                            }.bounds(leftButtonX, bottomY, buttonW, buttonH)
                            .build(),
                    )

                    addRenderableWidget(
                        Button
                            .builder(Component.literal("Back")) {
                                onClose()
                            }.bounds(rightButtonX, bottomY, buttonW, buttonH)
                            .build(),
                    )
                } else {
                    // Only show Back button when all installed or installing
                    addRenderableWidget(
                        Button
                            .builder(Component.literal("Back")) {
                                onClose()
                            }.bounds(centerX - buttonW / 2, bottomY, buttonW, buttonH)
                            .build(),
                    )
                }
            }

            State.SKIPPED -> {
                addRenderableWidget(
                    Button
                        .builder(Component.literal("I Understand, Proceed")) {
                            onClose() // Go to parent
                        }.bounds(centerX - (buttonW / 2), bottomY, buttonW, buttonH)
                        .build(),
                )
            }
        }
    }

    private fun startBackgroundInstallation() {
        Logger.info("Starting background library installation...")
        BackgroundLibraryInstaller.startBackgroundInstallation()
        // Switch to status view to show progress
        currentState = State.STATUS
        rebuildWidgets()
    }

    override fun render(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
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
            State.STATUS -> renderStatus(guiGraphics, centerX, centerY)
            State.SKIPPED -> renderSkipped(guiGraphics, centerX, centerY)
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun renderDisclaimer(
        gfx: GuiGraphics,
        cx: Int,
        cy: Int,
    ) {
        // Draw Info Text
        drawCenteredString(
            gfx,
            "This mod requires external libraries to function.",
            cx,
            60,
            ChatFormatting.GRAY.color ?: 0xAAAAAA,
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

    private fun renderStatus(
        gfx: GuiGraphics,
        cx: Int,
        cy: Int,
    ) {
        // Draw subtitle
        val allInstalled = BackgroundLibraryInstaller.areAllLibrariesInstalled()
        val isInstalling = BackgroundLibraryInstaller.isInstalling()

        val subtitleText =
            when {
                allInstalled -> "All libraries are installed and ready!"
                isInstalling -> "Installing libraries in background..."
                else -> "Some libraries are missing"
            }

        val subtitleColor =
            when {
                allInstalled -> ChatFormatting.GREEN.color ?: 0x00FF00
                isInstalling -> ChatFormatting.AQUA.color ?: 0x00AAFF
                else -> ChatFormatting.YELLOW.color ?: 0xFFAA00
            }

        drawCenteredString(gfx, subtitleText, cx, 60, subtitleColor)

        // Draw library status cards
        renderLibraryCards(gfx, cx, cy)
    }

    private fun renderLibraryCards(
        gfx: GuiGraphics,
        cx: Int,
        cy: Int,
    ) {
        val cardWidth = 320
        val cardHeight = 70
        val cardGap = 15
        val totalCardsHeight =
            (cardHeight * BackgroundLibraryInstaller.LibraryType.entries.size) +
                (cardGap * (BackgroundLibraryInstaller.LibraryType.entries.size - 1))
        // Start position that centers all cards vertically with proper spacing from bottom
        var currentY = cy - (totalCardsHeight / 2)

        BackgroundLibraryInstaller.LibraryType.entries.forEach { library ->
            val status = BackgroundLibraryInstaller.getStatus(library)

            // Card Background - color based on status
            val bgColor =
                when {
                    status.isComplete -> successColor
                    status.isFailed -> errorColor
                    status.isInstalling -> installingColor
                    else -> cardColor
                }

            gfx.fill(cx - cardWidth / 2, currentY, cx + cardWidth / 2, currentY + cardHeight, bgColor)
            gfx.renderOutline(cx - cardWidth / 2, currentY, cardWidth, cardHeight, borderColor)

            // Library Name
            val nameColor =
                when {
                    status.isComplete -> 0x00FF00
                    status.isFailed -> 0xFF5555
                    status.isInstalling -> 0x55AAFF
                    else -> 0xFFAA00
                }
            gfx.drawString(font, library.displayName, cx - cardWidth / 2 + 10, currentY + 8, nameColor, false)

            // Status indicator
            val statusIcon =
                when {
                    status.isComplete -> "✓"
                    status.isFailed -> "✗"
                    status.isInstalling -> "⟳"
                    else -> "○"
                }
            val statusText = "$statusIcon ${status.status}"
            gfx.drawString(
                font,
                statusText,
                cx + cardWidth / 2 - font.width(statusText) - 10,
                currentY + 8,
                0xDDDDDD,
                false,
            )

            // Progress bar (if installing)
            if (status.isInstalling || (status.progress > 0 && !status.isComplete)) {
                val barW = cardWidth - 40
                val barH = 6
                val barX = cx - barW / 2
                val barY = currentY + 30

                // Bar Background
                gfx.fill(barX, barY, barX + barW, barY + barH, 0xFF333333.toInt())

                // Bar Fill
                val fillW = (barW * status.progress.coerceIn(0f, 1f)).toInt()
                if (fillW > 0) {
                    gfx.fill(barX, barY, barX + fillW, barY + barH, 0xFF00AA00.toInt())
                }

                // Border
                gfx.renderOutline(barX - 1, barY - 1, barW + 2, barH + 2, 0xFF777777.toInt())

                // Speed info
                if (status.speed.isNotEmpty()) {
                    val speedText = "${(status.progress * 100).toInt()}% - ${status.speed}"
                    gfx.drawString(font, speedText, cx - font.width(speedText) / 2, barY + 10, 0xAAAAAA, false)
                }
            } else {
                // Status message when not installing
                val message =
                    when {
                        status.isComplete -> "Library is installed and ready to use"
                        status.isFailed -> "Installation failed - check logs or install manually"
                        else -> "Library not yet installed"
                    }
                gfx.drawString(font, message, cx - font.width(message) / 2, currentY + 35, 0xAAAAAA, false)
            }

            currentY += cardHeight + cardGap
        }
    }

    private fun renderSkipped(
        gfx: GuiGraphics,
        cx: Int,
        cy: Int,
    ) {
        val boxW = 400
        val boxH = 140
        val topY = cy - 70

        // Warning Box Background
        gfx.fill(cx - boxW / 2, topY, cx + boxW / 2, topY + boxH, warningColor)
        gfx.renderOutline(cx - boxW / 2, topY, boxW, boxH, 0xFFFF5555.toInt())

        // Warning Icon/Text
        val title =
            Component.literal("⚠ Manual Installation Required").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
        drawCenteredString(gfx, title.visualOrderText, cx, topY + 15, 0xFFFFFF)

        val lines =
            listOf(
                "You have skipped the automatic library download.",
                "The mod will NOT be able to play internet sources.",
                "",
                "Download yt-dlp and ffmpeg and place them (extracted) in:",
                "",
                FFMPEGProvider.directory
                    .toPath()
                    .toAbsolutePath()
                    .normalize()
                    .toString(),
                YTDLProvider.directory
                    .toPath()
                    .toAbsolutePath()
                    .normalize()
                    .toString(),
            )

        var textY = topY + 38
        lines.forEach { line ->
            val color = if (line.contains("/") || line.contains("\\")) 0xFFFF55 else 0xDDDDDD
            drawCenteredString(gfx, line, cx, textY, color)
            textY += 11
        }
    }

    // Helper for simple text centering
    private fun drawCenteredString(
        gfx: GuiGraphics,
        text: String,
        x: Int,
        y: Int,
        color: Int,
    ) {
        gfx.drawString(font, text, x - font.width(text) / 2, y, color, false)
    }

    // Helper to handle visual order text (components)
    private fun drawCenteredString(
        gfx: GuiGraphics,
        text: net.minecraft.util.FormattedCharSequence,
        x: Int,
        y: Int,
        color: Int = 0xFFFFFF,
    ) {
        gfx.drawString(font, text, x - font.width(text) / 2, y, color, false)
    }

    override fun onClose() {
        // Return to the previous screen (Main Menu usually)
        minecraft?.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = true

    override fun shouldCloseOnEsc(): Boolean = !BackgroundLibraryInstaller.isInstalling()

    // Refresh widgets periodically to update button states
    override fun tick() {
        super.tick()
        // Rebuild widgets every tick to update button states based on installation progress
        if (currentState == State.STATUS && (children().isEmpty() || BackgroundLibraryInstaller.isInstalling())) {
            rebuildWidgets()
        }
    }
}
