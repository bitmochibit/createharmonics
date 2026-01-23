@file:Suppress("SimplifyBooleanWithConstants")

package me.mochibit.createharmonics.client.gui

import me.mochibit.createharmonics.BuildConfig
import me.mochibit.createharmonics.audio.bin.BackgroundBinInstaller
import me.mochibit.createharmonics.audio.bin.BinStatusManager
import me.mochibit.createharmonics.audio.bin.FFMPEGProvider
import me.mochibit.createharmonics.audio.bin.YTDLProvider
import me.mochibit.createharmonics.registry.ModConfigurations
import me.mochibit.createharmonics.registry.ModLang
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen

class LibraryDisclaimerScreen(
    private val parent: Screen?,
) : Screen(ModLang.translate("gui.library_setup.title").component()) {
    private data class LibInfo(
        val name: String,
        val desc: String,
        val url: String,
    )

    private val libraries =
        listOf(
            LibInfo(
                "yt-dlp",
                ModLang.translate("gui.library_setup.ytdlp_desc").component().getString(512),
                "https://github.com/yt-dlp/yt-dlp",
            ),
            LibInfo(
                "FFmpeg",
                ModLang.translate("gui.library_setup.ffmpeg_desc").component().getString(512),
                "https://ffmpeg.org/",
            ),
        )

    // Track hovered card for tooltips
    private var hoveredCardIndex: Int? = null

    // State
    private enum class State { DISCLAIMER, STATUS, SKIPPED }

    private var currentState = State.DISCLAIMER

    // UI Constants
    private val cardColor = 0x50000000 // Semi-transparent black (slightly more opaque)
    private val borderColor = 0xFF444444.toInt()
    private val errorColor = 0x50FF0000 // Red tint (more opaque)
    private val installingColor = 0x5000AAFF // Blue tint (more opaque)
    private val warningColor = 0x50FF6600 // Orange tint for warnings
    private val headerColor = 0xFF00AA00.toInt() // Bright green for headers
    private val accentColor = 0xFFFFAA00.toInt() // Gold accent

    override fun init() {
        super.init()
        // Determine initial state based on installation status
        currentState =
            when {
                BinStatusManager.areAllLibrariesInstalled() -> State.STATUS
                BinStatusManager.isAnyInstalling() -> State.STATUS
                else -> State.DISCLAIMER
            }
        rebuildWidgets()
    }

    override fun rebuildWidgets() {
        clearWidgets()
        val buttonW = 150
        val buttonH = 24
        val buttonGap = 15
        val centerX = width / 2
        val bottomY = height - 40

        when (currentState) {
            State.DISCLAIMER -> {
                if (BuildConfig.IS_CURSEFORGE) {
                    addRenderableWidget(
                        Button
                            .builder(
                                ModLang.translate("gui.library_setup.manual_installation_btn").component(),
                            ) {
                                ModConfigurations.client.neverShowLibraryDisclaimer.set(true)
                                currentState = State.SKIPPED
                                rebuildWidgets()
                            }.bounds(centerX - buttonW / 2, bottomY, buttonW, buttonH)
                            .build(),
                    )
                } else {
                    // Calculate positions for two centered buttons with gap
                    val totalWidth = buttonW * 2 + buttonGap
                    val leftButtonX = centerX - totalWidth / 2
                    val rightButtonX = leftButtonX + buttonW + buttonGap

                    addRenderableWidget(
                        Button
                            .builder(
                                ModLang
                                    .translate("gui.library_setup.install_in_background_btn")
                                    .component()
                                    .withStyle(ChatFormatting.AQUA),
                            ) {
                                startBackgroundInstallation()
                            }.bounds(leftButtonX, bottomY, buttonW, buttonH)
                            .build(),
                    )

                    addRenderableWidget(
                        Button
                            .builder(ModLang.translate("gui.library_setup.manual_installation_btn").component()) {
                                // Set config to never show again
                                ModConfigurations.client.neverShowLibraryDisclaimer.set(true)
                                currentState = State.SKIPPED
                                rebuildWidgets()
                            }.bounds(rightButtonX, bottomY, buttonW, buttonH)
                            .build(),
                    )
                }
            }

            State.STATUS -> {
                val allInstalled = BinStatusManager.areAllLibrariesInstalled()
                val isInstalling = BinStatusManager.isAnyInstalling()

                if (!allInstalled && !isInstalling && !BuildConfig.IS_CURSEFORGE) {
                    val totalWidth = buttonW * 2 + buttonGap
                    val leftButtonX = centerX - totalWidth / 2
                    val rightButtonX = leftButtonX + buttonW + buttonGap

                    addRenderableWidget(
                        Button
                            .builder(
                                ModLang
                                    .translate("gui.library_setup.install_missing_lib_btn")
                                    .component()
                                    .withStyle(ChatFormatting.GREEN),
                            ) {
                                startBackgroundInstallation()
                            }.bounds(leftButtonX, bottomY, buttonW, buttonH)
                            .build(),
                    )

                    addRenderableWidget(
                        Button
                            .builder(
                                ModLang.translate("gui.library_setup.go_back_btn").component(),
                            ) {
                                onClose()
                            }.bounds(rightButtonX, bottomY, buttonW, buttonH)
                            .build(),
                    )
                } else {
                    addRenderableWidget(
                        Button
                            .builder(
                                ModLang.translate("gui.library_setup.go_back_btn").component(),
                            ) {
                                onClose()
                            }.bounds(centerX - buttonW / 2, bottomY, buttonW, buttonH)
                            .build(),
                    )
                }
            }

            State.SKIPPED -> {
                addRenderableWidget(
                    Button
                        .builder(ModLang.translate("gui.library_setup.manual_disclaimer_accept_btn").component()) {
                            onClose() // Go to parent
                        }.bounds(centerX - (buttonW / 2), bottomY, buttonW, buttonH)
                        .build(),
                )
            }
        }
    }

    private fun startBackgroundInstallation() {
        BackgroundBinInstaller.startBackgroundInstallation()
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

        guiGraphics.fillGradient(0, 0, width, height, 0xE0000000.toInt(), 0xD0000000.toInt())

        val centerX = width / 2
        val centerY = height / 2

        // Update hovered card index for disclaimer state
        if (currentState == State.DISCLAIMER) {
            updateHoveredCard(mouseX, mouseY, centerX)
        }

        val titleScale = 1.8f
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(centerX.toDouble(), 25.0, 0.0)
        guiGraphics.pose().scale(titleScale, titleScale, 1f)

        // Draw shadow
        drawCenteredString(guiGraphics, "Create: Harmonics", 1, 1, 0x000000)
        // Draw title with gradient effect (gold color)
        drawCenteredString(guiGraphics, "Create: Harmonics", 0, 0, accentColor)
        guiGraphics.pose().popPose()

        when (currentState) {
            State.DISCLAIMER -> renderDisclaimer(guiGraphics, centerX, centerY)
            State.STATUS -> renderStatus(guiGraphics, centerX, centerY)
            State.SKIPPED -> renderSkipped(guiGraphics, centerX, centerY)
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun updateHoveredCard(
        mouseX: Int,
        mouseY: Int,
        centerX: Int,
    ) {
        val cardWidth = 280
        val cardHeight = 35
        val cardGap = 8
        val startY = 95
        var currentY = startY

        hoveredCardIndex = null

        libraries.forEachIndexed { index, _ ->
            val cardLeft = centerX - cardWidth / 2
            val cardRight = centerX + cardWidth / 2
            val cardTop = currentY
            val cardBottom = currentY + cardHeight

            if (mouseX in cardLeft..cardRight && mouseY in cardTop..cardBottom) {
                hoveredCardIndex = index
            }

            currentY += cardHeight + cardGap
        }
    }

    private fun renderDisclaimer(
        gfx: GuiGraphics,
        cx: Int,
        cy: Int,
    ) {
        // Draw subtitle
        val subtitleKey =
            if (BuildConfig.IS_CURSEFORGE) {
                "gui.library_setup.library_disclaimer_subtitle_curseforge"
            } else {
                "gui.library_setup.library_disclaimer_subtitle"
            }

        drawCenteredString(
            gfx,
            ModLang.translate(subtitleKey).component().getString(128),
            cx,
            55,
            accentColor,
        )

        val infoKey =
            if (BuildConfig.IS_CURSEFORGE) {
                "gui.library_setup.library_disclaimer_info_curseforge"
            } else {
                "gui.library_setup.library_disclaimer_info"
            }

        val disclaimerInfoText =
            wrapText(
                ModLang.translate(infoKey).component().getString(256),
                400,
            )
        for (i in disclaimerInfoText.indices) {
            drawCenteredString(
                gfx,
                disclaimerInfoText[i],
                cx,
                68 + i * 11,
                ChatFormatting.GRAY.color ?: 0xAAAAAA,
            )
        }

        // Compact cards - much smaller
        val cardWidth = 280
        val cardHeight = 35
        val cardGap = 8
        val startY = 95
        var currentY = startY

        libraries.forEachIndexed { index, lib ->
            val isHovered = hoveredCardIndex == index

            // Card Background with hover effect
            val cardBg = if (isHovered) 0x70000000 else cardColor
            gfx.fill(cx - cardWidth / 2, currentY, cx + cardWidth / 2, currentY + cardHeight, cardBg)

            // Top accent line
            gfx.fill(cx - cardWidth / 2, currentY, cx + cardWidth / 2, currentY + 2, 0xFF00AAFF.toInt())

            // Border with hover highlight
            val borderCol = if (isHovered) 0xFF6699FF.toInt() else borderColor
            gfx.renderOutline(cx - cardWidth / 2, currentY, cardWidth, cardHeight, borderCol)

            // Lib Name with icon - centered vertically
            val nameText = "▸ ${lib.name}"
            val textY = currentY + (cardHeight - font.lineHeight) / 2
            gfx.drawString(font, nameText, cx - cardWidth / 2 + 10, textY, accentColor, false)

            currentY += cardHeight + cardGap
        }

        // Render tooltip for hovered card
        hoveredCardIndex?.let { index ->
            if (index in libraries.indices) {
                val lib = libraries[index]
                renderLibraryTooltip(gfx, lib, cx, cy)
            }
        }
    }

    private fun renderLibraryTooltip(
        gfx: GuiGraphics,
        lib: LibInfo,
        screenCenterX: Int,
        screenCenterY: Int,
    ) {
        val tooltipWidth = 320
        val tooltipHeight = 90
        val tooltipX = screenCenterX - tooltipWidth / 2
        val tooltipY = screenCenterY - tooltipHeight / 2 - 20

        // Tooltip background with stronger opacity
        gfx.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, 0xE0000000.toInt())
        gfx.renderOutline(tooltipX, tooltipY, tooltipWidth, tooltipHeight, 0xFF6699FF.toInt())

        // Title bar
        gfx.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + 2, 0xFF00AAFF.toInt())

        var textY = tooltipY + 8

        // Library name
        gfx.drawString(font, lib.name, tooltipX + 10, textY, accentColor, false)
        textY += 14

        // Description with word wrap
        val maxWidth = tooltipWidth - 20
        val wrappedDesc = wrapText(lib.desc, maxWidth)
        wrappedDesc.forEach { line ->
            gfx.drawString(font, line, tooltipX + 10, textY, 0xCCCCCC, false)
            textY += 11
        }

        textY += 3
        // URL
        gfx.drawString(font, lib.url, tooltipX + 10, textY, 0x5599FF, false)

        textY += 20

        val hintText = ModLang.translate("gui.library_setup.card_tooltip.click_to_open_site").component().getString(128)
        gfx.drawString(font, hintText, tooltipX + 10, textY, 0xCCCCCC, false)
    }

    private fun wrapText(
        text: String,
        maxWidth: Int,
    ): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""

        words.forEach { word ->
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (font.width(testLine) <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                }
                currentLine = word
            }
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }

        return lines
    }

    private fun renderStatus(
        gfx: GuiGraphics,
        cx: Int,
        cy: Int,
    ) {
        val allInstalled = BinStatusManager.areAllLibrariesInstalled()
        val isInstalling = BinStatusManager.isAnyInstalling()

        val subtitleText =
            when {
                allInstalled -> {
                    ModLang
                        .translate("gui.library_setup.status.all_lib_installed")
                        .component()
                        .getString(128)
                }

                isInstalling -> {
                    ModLang.translate("gui.library_setup.status.installing_lib").component().getString(128)
                }

                else -> {
                    ModLang.translate("gui.library_setup.status.lib_are_missing").component().getString(128)
                }
            }

        val subtitleColor =
            when {
                allInstalled -> headerColor
                isInstalling -> 0xFF00AAFF.toInt()
                else -> accentColor
            }

        drawCenteredString(gfx, subtitleText, cx, 55, subtitleColor)

        // Draw library status cards
        renderLibraryCards(gfx, cx, cy)
    }

    private fun renderLibraryCards(
        gfx: GuiGraphics,
        cx: Int,
        cy: Int,
    ) {
        val cardWidth = 340
        val installingCardHeight = 60 // Full height for cards with progress bars
        val compactCardHeight = 50 // Compact height for completed/failed/pending cards
        val cardGap = 10 // Reduced from 12 to 10
        val libraryCount = BinStatusManager.LibraryType.entries.size

        // Calculate total height based on each library's state
        var totalCardsHeight = 0
        BinStatusManager.LibraryType.entries.forEach { library ->
            val status = BinStatusManager.getStatus(library)
            val height =
                if (status.isInstalling || (status.progress > 0 && !status.isComplete)) {
                    installingCardHeight
                } else {
                    compactCardHeight
                }
            totalCardsHeight += height
        }
        totalCardsHeight += cardGap * (libraryCount - 1)

        // Calculate positioning with proper margins - 75px from top, 70px from bottom for buttons
        val topPadding = 75
        val bottomPadding = 70 // Increased to ensure no overlap
        val availableHeight = height - topPadding - bottomPadding

        // Start position - center cards in available space
        val startY =
            if (totalCardsHeight < availableHeight) {
                topPadding + (availableHeight - totalCardsHeight) / 2
            } else {
                topPadding
            }

        var currentY = startY

        BinStatusManager.LibraryType.entries.forEach { library ->
            val status = BinStatusManager.getStatus(library)

            // Determine card height based on status
            val cardHeight =
                if (status.isInstalling || (status.progress > 0 && !status.isComplete)) {
                    installingCardHeight
                } else {
                    compactCardHeight
                }

            val bgColor =
                when {
                    status.isComplete -> cardColor

                    // Keep installed libraries transparent like normal cards
                    status.isFailed -> errorColor

                    status.isInstalling -> installingColor

                    else -> cardColor
                }

            gfx.fill(cx - cardWidth / 2, currentY, cx + cardWidth / 2, currentY + cardHeight, bgColor)

            // Top colored accent bar (3px height)
            val accentBarColor =
                when {
                    status.isComplete -> 0xFF00FF00.toInt()
                    status.isFailed -> 0xFFFF5555.toInt()
                    status.isInstalling -> 0xFF00AAFF.toInt()
                    else -> accentColor
                }
            gfx.fill(cx - cardWidth / 2, currentY, cx + cardWidth / 2, currentY + 3, accentBarColor)

            // Border
            gfx.renderOutline(cx - cardWidth / 2, currentY, cardWidth, cardHeight, borderColor)

            // Library Name with better styling
            val nameColor =
                when {
                    status.isComplete -> 0x00FF00
                    status.isFailed -> 0xFF5555
                    status.isInstalling -> 0x55AAFF
                    else -> accentColor
                }
            val nameText = "▸ ${library.displayName}"
            gfx.drawString(font, nameText, cx - cardWidth / 2 + 12, currentY + 8, nameColor, false)

            // TODO Maybe add some icon representation in future?
            val statusText = formatStatus(status.status)
            gfx.drawString(
                font,
                statusText,
                cx + cardWidth / 2 - font.width(statusText) - 12,
                currentY + 8,
                0xEEEEEE,
                false,
            )

            // Progress bar (if installing or in progress)
            if (status.isInstalling || (status.progress > 0 && !status.isComplete)) {
                val barW = cardWidth - 50
                val barH = 6 // Reduced from 8 to 6
                val barX = cx - barW / 2
                val barY = currentY + 26 // Adjusted position

                // Bar Background with subtle styling
                gfx.fill(barX, barY, barX + barW, barY + barH, 0xFF222222.toInt())

                // Bar Fill with gradient effect
                val fillW = (barW * status.progress.coerceIn(0f, 1f)).toInt()
                if (fillW > 0) {
                    gfx.fill(barX, barY, barX + fillW, barY + barH, 0xFF00CC00.toInt())
                    // Add lighter top portion for 3D effect
                    gfx.fill(barX, barY, barX + fillW, barY + 2, 0xFF00FF00.toInt())
                }

                // Border with rounded corners effect
                gfx.renderOutline(barX - 1, barY - 1, barW + 2, barH + 2, 0xFF666666.toInt())

                // Progress percentage and speed - moved closer to bar
                if (status.speed.isNotEmpty()) {
                    val progressText = "${(status.progress * 100).toInt()}% • ${status.speed}"
                    gfx.drawString(font, progressText, cx - font.width(progressText) / 2, barY + 10, 0xBBBBBB, false)
                } else {
                    val progressText = "${(status.progress * 100).toInt()}%"
                    gfx.drawString(font, progressText, cx - font.width(progressText) / 2, barY + 10, 0xBBBBBB, false)
                }
            } else {
                // Status message when not installing - centered vertically in compact card
                val message =
                    when {
                        status.isComplete -> {
                            ModLang
                                .translate("gui.library_setup.status.lib_ready_to_use")
                                .component()
                                .getString(128)
                        }

                        status.isFailed -> {
                            ModLang
                                .translate("gui.library_setup.status.lib_failed_to_install")
                                .component()
                                .getString(128)
                        }

                        else -> {
                            ModLang
                                .translate("gui.library_setup.status.lib_waiting_to_install")
                                .component()
                                .getString(128)
                        }
                    }
                val messageColor =
                    when {
                        status.isComplete -> 0x88FF88
                        status.isFailed -> 0xFF8888
                        else -> 0xBBBBBB
                    }
                // Center the message vertically in the card (accounting for library name at top)
                val messageY = currentY + 8 + font.lineHeight + 3
                gfx.drawString(font, message, cx - font.width(message) / 2, messageY, messageColor, false)
            }

            currentY += cardHeight + cardGap
        }
    }

    private fun renderSkipped(
        gfx: GuiGraphics,
        cx: Int,
        cy: Int,
    ) {
        val boxW = 460
        val boxH = 160
        val topY = cy - 80

        // Warning Box Background
        gfx.fill(cx - boxW / 2, topY, cx + boxW / 2, topY + boxH, warningColor)
        gfx.renderOutline(cx - boxW / 2, topY, boxW, boxH, 0xFFFF5555.toInt())

        // Warning Icon/Text
        val title =
            ModLang
                .translate("gui.library_setup.manual_install_title")
                .component()
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
        drawCenteredString(gfx, title.visualOrderText, cx, topY + 12, 0xFFFFFF)

        val lines =
            listOf(
                ModLang.translate("gui.library_setup.manual_notice_1").component().getString(256),
                ModLang.translate("gui.library_setup.manual_notice_2").component().getString(256),
                "",
                ModLang.translate("gui.library_setup.manual_notice_instruct").component().getString(256),
            )

        var textY = topY + 32
        lines.forEach { line ->
            drawCenteredString(gfx, line, cx, textY, 0xDDDDDD)
            textY += 11
        }

        // Show directory paths with smaller font and word wrapping
        textY += 5
        val paths =
            listOf(
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

        paths.forEach { path ->
            // Truncate path if too long for the box
            val maxWidth = boxW - 40
            val pathWidth = font.width(path)
            val displayPath =
                if (pathWidth > maxWidth) {
                    // Try to show end of path which is most relevant
                    "..." + path.substring(path.length - (path.length * maxWidth / pathWidth))
                } else {
                    path
                }
            drawCenteredString(gfx, displayPath, cx, textY, 0xFFFF55)
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

    override fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
        button: Int,
    ): Boolean {
        // Handle card clicks in disclaimer state
        if (currentState == State.DISCLAIMER && button == 0) {
            hoveredCardIndex?.let { index ->
                if (index in libraries.indices) {
                    val lib = libraries[index]
                    // Open URL
                    net.minecraft.Util
                        .getPlatform()
                        .openUri(lib.url)
                    return true
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun onClose() {
        // Return to the previous screen (Main Menu usually)
        minecraft?.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = true

    override fun shouldCloseOnEsc(): Boolean = !BackgroundBinInstaller.isInstalling()

    private fun formatStatus(status: BinStatusManager.Status): String =
        when (status) {
            BinStatusManager.Status.PENDING -> {
                ModLang
                    .translate(
                        "gui.library_setup.status.single_lib.pending",
                    ).component()
                    .getString(128)
            }

            BinStatusManager.Status.NOT_INSTALLED -> {
                ModLang
                    .translate(
                        "gui.library_setup.status.single_lib.not_installed",
                    ).component()
                    .getString(128)
            }

            BinStatusManager.Status.DOWNLOADING -> {
                ModLang
                    .translate(
                        "gui.library_setup.status.single_lib.downloading",
                    ).component()
                    .getString(128)
            }

            BinStatusManager.Status.EXTRACTING -> {
                ModLang
                    .translate(
                        "gui.library_setup.status.single_lib.extracting",
                    ).component()
                    .getString(128)
            }

            BinStatusManager.Status.INSTALLED -> {
                ModLang
                    .translate(
                        "gui.library_setup.status.single_lib.installed",
                    ).component()
                    .getString(128)
            }

            BinStatusManager.Status.ALREADY_INSTALLED -> {
                ModLang
                    .translate(
                        "gui.library_setup.status.single_lib.already_installed",
                    ).component()
                    .getString(128)
            }

            BinStatusManager.Status.FAILED -> {
                ModLang
                    .translate(
                        "gui.library_setup.status.single_lib.failed",
                    ).component()
                    .getString(128)
            }

            BinStatusManager.Status.ERROR -> {
                ModLang
                    .translate(
                        "gui.library_setup.status.single_lib.error",
                    ).component()
                    .getString(128)
            }
        }

    // Refresh widgets periodically to update button states
    override fun tick() {
        super.tick()
        // Rebuild widgets every tick to update button states based on installation progress
        if (currentState == State.STATUS && (children().isEmpty() || BackgroundBinInstaller.isInstalling())) {
            rebuildWidgets()
        }
    }
}
