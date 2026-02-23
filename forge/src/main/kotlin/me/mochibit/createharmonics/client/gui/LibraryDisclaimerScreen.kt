@file:Suppress("SimplifyBooleanWithConstants")

package me.mochibit.createharmonics.client.gui

import com.simibubi.create.foundation.gui.AllIcons
import com.simibubi.create.foundation.gui.widget.IconButton
import me.mochibit.createharmonics.BuildConfig
import me.mochibit.createharmonics.audio.bin.BackgroundBinInstaller
import me.mochibit.createharmonics.audio.bin.BinProvider
import me.mochibit.createharmonics.audio.bin.BinStatusManager
import me.mochibit.createharmonics.audio.bin.FFMPEGProvider
import me.mochibit.createharmonics.audio.bin.YTDLProvider
import me.mochibit.createharmonics.extension.drawCenteredString
import me.mochibit.createharmonics.extension.toMultilineFormattedCharSequence
import me.mochibit.createharmonics.registry.ModConfigurations
import me.mochibit.createharmonics.registry.ModLang
import net.minecraft.ChatFormatting
import net.minecraft.Util
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence

class LibraryDisclaimerScreen(
    private val parent: Screen?,
) : Screen(ModLang.translate("gui.library_setup.title").component()) {
    // Data classes for better organization
    private data class LibInfo(
        val name: String,
        val desc: String,
        val url: String,
    )

    private data class CardDimensions(
        val width: Int,
        val height: Int,
        val x: Int,
        val y: Int,
    ) {
        val left: Int get() = x - width / 2
        val right: Int get() = x + width / 2
        val top: Int get() = y
        val bottom: Int get() = y + height

        fun contains(
            mouseX: Int,
            mouseY: Int,
        ): Boolean = mouseX in left..right && mouseY in top..bottom
    }

    // UI State
    private enum class State { DISCLAIMER, STATUS, SKIPPED }

    private var currentState = State.DISCLAIMER
    private var hoveredCardIndex: Int? = null
    private val cardDimensions = mutableListOf<CardDimensions>()

    // Track installation states to detect changes
    private var previousInstallationStates =
        mutableMapOf<BinStatusManager.LibraryType, BinStatusManager.InstallationStatus>()

    // Constants - centralized for easier theming
    private object Theme {
        const val CARD_BG = 0x50000000
        const val CARD_BG_HOVER = 0x70000000
        const val BORDER = 0xFF444444.toInt()
        const val BORDER_HOVER = 0xFF6699FF.toInt()
        const val ACCENT = 0xFFFFAA00.toInt()
        const val ACCENT_BRIGHT = 0xFF00AAFF.toInt()
        const val HEADER = 0xFF00AA00.toInt()
        const val ERROR = 0x50FF0000
        const val INSTALLING = 0x5000AAFF
        const val WARNING = 0x50FF6600

        const val GREEN = 0x00FF00
        const val GREEN_LIGHT = 0x88FF88
        const val RED = 0xFF5555
        const val RED_LIGHT = 0xFF8888
        const val BLUE = 0x55AAFF
        const val GRAY = 0xAAAAAA
        const val GRAY_LIGHT = 0xBBBBBB
        const val GRAY_DARK = 0x666666
        const val WHITE = 0xEEEEEE
    }

    private object Layout {
        const val BUTTON_WIDTH = 150
        const val BUTTON_HEIGHT = 24
        const val BUTTON_GAP = 15
        const val CARD_GAP = 10
        const val PADDING_TOP = 75
        const val PADDING_BOTTOM = 70
        const val TITLE_Y = 25
        const val SUBTITLE_Y = 55

        // Responsive card sizing
        fun getCardWidth(screenWidth: Int): Int = (screenWidth * 0.5f).toInt().coerceIn(280, 400)

        fun getCompactCardHeight(): Int = 50

        fun getExpandedCardHeight(): Int = 60
    }

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

    override fun init() {
        super.init()
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
        cardDimensions.clear()

        when (currentState) {
            State.DISCLAIMER -> buildDisclaimerButtons()
            State.STATUS -> buildStatusButtons()
            State.SKIPPED -> buildSkippedButtons()
        }
    }

    // Simplified button building with common pattern extraction
    private fun buildDisclaimerButtons() {
        val cx = width / 2
        val bottomY = height - 40

        if (BuildConfig.IS_CURSEFORGE) {
            addCenteredButton(
                ModLang.translate("gui.library_setup.manual_installation_btn").component(),
                cx,
                bottomY,
            ) {
                ModConfigurations.client.neverShowLibraryDisclaimer.set(true)
                currentState = State.SKIPPED
                rebuildWidgets()
            }
        } else {
            addButtonPair(
                cx,
                bottomY,
                left =
                    ButtonData(
                        ModLang
                            .translate("gui.library_setup.install_in_background_btn")
                            .component()
                            .withStyle(ChatFormatting.AQUA),
                    ) { startBackgroundInstallation() },
                right =
                    ButtonData(
                        ModLang.translate("gui.library_setup.manual_installation_btn").component(),
                    ) {
                        ModConfigurations.client.neverShowLibraryDisclaimer.set(true)
                        currentState = State.SKIPPED
                        rebuildWidgets()
                    },
            )
        }
    }

    private fun buildStatusButtons() {
        val cx = width / 2
        val bottomY = height - 40
        val allInstalled = BinStatusManager.areAllLibrariesInstalled()
        val isInstalling = BinStatusManager.isAnyInstalling()

        // Add delete buttons for installed libraries
        addDeleteButtonsForLibraries()

        val (leftButton, rightButton) =
            when {
                !allInstalled && !isInstalling && !BuildConfig.IS_CURSEFORGE -> {
                    ButtonData(
                        ModLang
                            .translate("gui.library_setup.install_missing_lib_btn")
                            .component()
                            .withStyle(ChatFormatting.GREEN),
                    ) { startBackgroundInstallation() } to
                        ButtonData(
                            ModLang.translate("gui.library_setup.go_back_btn").component(),
                        ) { onClose() }
                }

                else -> {
                    ButtonData(
                        ModLang.translate("gui.library_setup.open_folder_btn").component(),
                    ) {
                        BinStatusManager.ensureBinaryFolders()
                        Util.getPlatform().openFile(BinProvider.providersFolder)
                    } to
                        ButtonData(
                            ModLang.translate("gui.library_setup.go_back_btn").component(),
                        ) { onClose() }
                }
            }

        addButtonPair(cx, bottomY, leftButton, rightButton)
    }

    private fun buildSkippedButtons() {
        val cx = width / 2
        val bottomY = height - 40

        addButtonPair(
            cx,
            bottomY,
            left =
                ButtonData(
                    ModLang.translate("gui.library_setup.open_folder_btn").component(),
                ) {
                    BinStatusManager.ensureBinaryFolders()
                    Util.getPlatform().openFile(BinProvider.providersFolder)
                },
            right =
                ButtonData(
                    ModLang.translate("gui.library_setup.manual_disclaimer_accept_btn").component(),
                ) { onClose() },
        )
    }

    // Helper classes and methods for button creation
    private data class ButtonData(
        val label: Component,
        val action: (button: Button) -> Unit,
    )

    private fun addCenteredButton(
        label: Component,
        x: Int,
        y: Int,
        action: (button: Button) -> Unit,
    ) {
        addRenderableWidget(
            Button
                .builder(label, action)
                .bounds(x - Layout.BUTTON_WIDTH / 2, y, Layout.BUTTON_WIDTH, Layout.BUTTON_HEIGHT)
                .build(),
        )
    }

    private fun addButtonPair(
        x: Int,
        y: Int,
        left: ButtonData,
        right: ButtonData,
    ) {
        val totalWidth = Layout.BUTTON_WIDTH * 2 + Layout.BUTTON_GAP
        val leftX = x - totalWidth / 2
        val rightX = leftX + Layout.BUTTON_WIDTH + Layout.BUTTON_GAP

        addRenderableWidget(
            Button
                .builder(left.label, left.action)
                .bounds(leftX, y, Layout.BUTTON_WIDTH, Layout.BUTTON_HEIGHT)
                .build(),
        )
        addRenderableWidget(
            Button
                .builder(right.label, right.action)
                .bounds(rightX, y, Layout.BUTTON_WIDTH, Layout.BUTTON_HEIGHT)
                .build(),
        )
    }

    private fun addDeleteButtonsForLibraries() {
        val positions = calculateCardPositions()

        BinStatusManager.LibraryType.entries.forEachIndexed { index, library ->
            // Check if library is actually installed (not just status)
            val isActuallyInstalled = BinStatusManager.isLibraryInstalled(library)
            val status = BinStatusManager.getStatus(library)

            // Show delete button for installed libraries that are not currently installing
            if (isActuallyInstalled && !status.isInstalling && index < positions.size) {
                val pos = positions[index]
                val buttonSize = 16 // Icon button is typically 16x16
                val paddingX = 8
                val paddingY = 8
                // Align to right edge of card with padding
                val buttonX = pos.right - buttonSize - paddingX
                // Center vertically or place near top with padding
                val buttonY = pos.bottom - buttonSize - paddingY

                val deleteButton =
                    IconButton(buttonX, buttonY, AllIcons.I_CONFIG_DISCARD)
                        .withCallback<IconButton> {
                            deleteLibrary(library)
                        }.apply {
                            setToolTip(
                                ModLang.translate("gui.library_setup.delete_library_tooltip").component(),
                            )
                        }

                addRenderableWidget(
                    deleteButton,
                )
            }
        }
    }

    private fun deleteLibrary(library: BinStatusManager.LibraryType) {
        val provider =
            when (library) {
                BinStatusManager.LibraryType.YTDLP -> YTDLProvider
                BinStatusManager.LibraryType.FFMPEG -> FFMPEGProvider
            }

        provider.directory.takeIf { it.exists() }?.deleteRecursively()
        BinStatusManager.resetStatus(library)
        rebuildWidgets()
    }

    private fun startBackgroundInstallation() {
        if (!BuildConfig.IS_CURSEFORGE) {
            BackgroundBinInstaller.startBackgroundInstallation()
            currentState = State.STATUS
            rebuildWidgets()
        }
    }

    // Rendering
    override fun render(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        renderBackground(guiGraphics)
        guiGraphics.fillGradient(0, 0, width, height, 0xE0000000.toInt(), 0xD0000000.toInt())

        renderTitle(guiGraphics)

        when (currentState) {
            State.DISCLAIMER -> renderDisclaimer(guiGraphics, mouseX, mouseY)
            State.STATUS -> renderStatus(guiGraphics)
            State.SKIPPED -> renderSkipped(guiGraphics)
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun renderTitle(gfx: GuiGraphics) {
        val titleScale = 1.8f
        gfx.pose().pushPose()
        gfx.pose().translate((width / 2).toDouble(), Layout.TITLE_Y.toDouble(), 0.0)
        gfx.pose().scale(titleScale, titleScale, 1f)
        gfx.drawCenteredString(font, Component.literal("Create: Harmonics"), 1, 1, 0x000000, 200)
        gfx.drawCenteredString(font, Component.literal("Create: Harmonics"), 0, 0, Theme.ACCENT, 200)
        gfx.pose().popPose()
    }

    private fun renderDisclaimer(
        gfx: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
    ) {
        val cx = width / 2
        val cy = height / 2

        // Subtitle
        val subtitleKey =
            if (BuildConfig.IS_CURSEFORGE) {
                "gui.library_setup.library_disclaimer_subtitle_curseforge"
            } else {
                "gui.library_setup.library_disclaimer_subtitle"
            }
        gfx.drawCenteredString(
            font,
            ModLang.translate(subtitleKey).component(),
            cx,
            Layout.SUBTITLE_Y,
            Theme.ACCENT,
            400,
        )

        // Info text
        val infoKey =
            if (BuildConfig.IS_CURSEFORGE) {
                "gui.library_setup.library_disclaimer_info_curseforge"
            } else {
                "gui.library_setup.library_disclaimer_info"
            }
        renderWrappedText(gfx, ModLang.translate(infoKey).component(), cx, 68, 400, Theme.GRAY)

        // Library cards
        renderDisclaimerCards(gfx, cx, mouseX, mouseY)

        // Tooltip for hovered card
        hoveredCardIndex?.let { index ->
            if (index in libraries.indices) {
                renderLibraryTooltip(gfx, libraries[index], cx, cy)
            }
        }
    }

    private fun renderDisclaimerCards(
        gfx: GuiGraphics,
        cx: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        cardDimensions.clear()
        val cardWidth = 280
        val cardHeight = 35
        var currentY = 95

        hoveredCardIndex = null

        libraries.forEachIndexed { index, lib ->
            val dims = CardDimensions(cardWidth, cardHeight, cx, currentY)
            cardDimensions.add(dims)

            val isHovered = dims.contains(mouseX, mouseY)
            if (isHovered) hoveredCardIndex = index

            // Card background
            gfx.fill(
                dims.left,
                dims.top,
                dims.right,
                dims.bottom,
                if (isHovered) Theme.CARD_BG_HOVER else Theme.CARD_BG,
            )

            // Top accent
            gfx.fill(dims.left, dims.top, dims.right, dims.top + 2, Theme.ACCENT_BRIGHT)

            // Border
            gfx.renderOutline(
                dims.left,
                dims.top,
                dims.width,
                dims.height,
                if (isHovered) Theme.BORDER_HOVER else Theme.BORDER,
            )

            // Library name
            val nameText = "▸ ${lib.name}"
            val textY = dims.top + (dims.height - font.lineHeight) / 2
            gfx.drawString(font, nameText, dims.left + 10, textY, Theme.ACCENT, false)

            currentY += cardHeight + Layout.CARD_GAP
        }
    }

    private fun renderLibraryTooltip(
        gfx: GuiGraphics,
        lib: LibInfo,
        screenCenterX: Int,
        screenCenterY: Int,
    ) {
        val tooltipWidth = 320
        val padding = 10
        val maxDescWidth = tooltipWidth - (padding * 2)

        val wrappedDesc = font.split(Component.literal(lib.desc), maxDescWidth)
        val tooltipHeight =
            padding + font.lineHeight + 4 + (wrappedDesc.size * font.lineHeight) +
                4 + font.lineHeight + 4 + font.lineHeight + padding

        val tooltipX = screenCenterX - tooltipWidth / 2
        val tooltipY = screenCenterY - tooltipHeight / 2 - 20

        // Background
        gfx.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, 0xE0000000.toInt())
        gfx.renderOutline(tooltipX, tooltipY, tooltipWidth, tooltipHeight, Theme.BORDER_HOVER)
        gfx.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + 2, Theme.ACCENT_BRIGHT)

        var textY = tooltipY + padding

        // Content
        gfx.drawString(font, lib.name, tooltipX + padding, textY, Theme.ACCENT, false)
        textY += font.lineHeight + 4

        wrappedDesc.forEach { line ->
            gfx.drawString(font, line, tooltipX + padding, textY, 0xCCCCCC, false)
            textY += font.lineHeight
        }
        textY += 4

        gfx.drawString(font, lib.url, tooltipX + padding, textY, 0x5599FF, false)
        textY += font.lineHeight + 4

        val hintText = ModLang.translate("gui.library_setup.card_tooltip.click_to_open_site").component().getString(128)
        gfx.drawString(font, hintText, tooltipX + padding, textY, 0xCCCCCC, false)
    }

    private fun renderStatus(gfx: GuiGraphics) {
        val cx = width / 2
        val allInstalled = BinStatusManager.areAllLibrariesInstalled()
        val isInstalling = BinStatusManager.isAnyInstalling()

        val (subtitleText, subtitleColor) =
            when {
                allInstalled -> {
                    ModLang
                        .translate("gui.library_setup.status.all_lib_installed")
                        .component()
                        .getString(128) to Theme.HEADER
                }

                isInstalling -> {
                    ModLang.translate("gui.library_setup.status.installing_lib").component().getString(128) to
                        Theme.ACCENT_BRIGHT
                }

                else -> {
                    ModLang
                        .translate("gui.library_setup.status.lib_are_missing")
                        .component()
                        .getString(128) to Theme.ACCENT
                }
            }

        gfx.drawCenteredString(font, Component.literal(subtitleText), cx, Layout.SUBTITLE_Y, subtitleColor, 200)
        renderLibraryCards(gfx, cx)
    }

    private fun calculateCardPositions(): List<CardDimensions> {
        val cx = width / 2
        val cardWidth = Layout.getCardWidth(width)
        val positions = mutableListOf<CardDimensions>()

        var totalHeight = 0
        val heights =
            BinStatusManager.LibraryType.entries.map { library ->
                val status = BinStatusManager.getStatus(library)
                if (status.isInstalling || (status.progress > 0 && !status.isComplete)) {
                    Layout.getExpandedCardHeight()
                } else {
                    Layout.getCompactCardHeight()
                }.also { totalHeight += it }
            }
        totalHeight += Layout.CARD_GAP * (heights.size - 1)

        val availableHeight = height - Layout.PADDING_TOP - Layout.PADDING_BOTTOM
        var currentY =
            if (totalHeight < availableHeight) {
                Layout.PADDING_TOP + (availableHeight - totalHeight) / 2
            } else {
                Layout.PADDING_TOP
            }

        heights.forEach { cardHeight ->
            positions.add(CardDimensions(cardWidth, cardHeight, cx, currentY))
            currentY += cardHeight + Layout.CARD_GAP
        }

        return positions
    }

    private fun renderLibraryCards(
        gfx: GuiGraphics,
        cx: Int,
    ) {
        val positions = calculateCardPositions()

        BinStatusManager.LibraryType.entries.forEachIndexed { index, library ->
            if (index >= positions.size) return@forEachIndexed

            val pos = positions[index]
            val status = BinStatusManager.getStatus(library)

            renderLibraryCard(gfx, pos, library, status)
        }
    }

    private fun renderLibraryCard(
        gfx: GuiGraphics,
        dims: CardDimensions,
        library: BinStatusManager.LibraryType,
        status: BinStatusManager.InstallationStatus,
    ) {
        // Background
        val bgColor =
            when {
                status.isComplete -> Theme.CARD_BG
                status.isFailed -> Theme.ERROR
                status.isInstalling -> Theme.INSTALLING
                else -> Theme.CARD_BG
            }
        gfx.fill(dims.left, dims.top, dims.right, dims.bottom, bgColor)

        // Top accent
        val accentColor =
            when {
                status.isComplete -> 0xFF00FF00.toInt()
                status.isFailed -> 0xFFFF5555.toInt()
                status.isInstalling -> Theme.ACCENT_BRIGHT
                else -> Theme.ACCENT
            }
        gfx.fill(dims.left, dims.top, dims.right, dims.top + 3, accentColor)

        // Border
        gfx.renderOutline(dims.left, dims.top, dims.width, dims.height, Theme.BORDER)

        // Library name
        val nameColor =
            when {
                status.isComplete -> Theme.GREEN
                status.isFailed -> Theme.RED
                status.isInstalling -> Theme.BLUE
                else -> Theme.ACCENT
            }
        gfx.drawString(font, "▸ ${library.displayName}", dims.left + 12, dims.top + 8, nameColor, false)

        // Status text
        val statusText = formatStatus(status.status)
        gfx.drawString(font, statusText, dims.right - font.width(statusText) - 12, dims.top + 8, Theme.WHITE, false)

        // Progress or message
        if (status.isInstalling || (status.progress > 0 && !status.isComplete)) {
            renderProgressBar(gfx, dims, status)
        } else {
            renderStatusMessage(gfx, dims, status)
        }
    }

    private fun renderProgressBar(
        gfx: GuiGraphics,
        dims: CardDimensions,
        status: BinStatusManager.InstallationStatus,
    ) {
        val barW = dims.width - 50
        val barH = 6
        val barX = dims.x - barW / 2
        val barY = dims.top + 26

        // Background
        gfx.fill(barX, barY, barX + barW, barY + barH, 0xFF222222.toInt())

        // Fill
        val fillW = (barW * status.progress.coerceIn(0f, 1f)).toInt()
        if (fillW > 0) {
            gfx.fill(barX, barY, barX + fillW, barY + barH, 0xFF00CC00.toInt())
            gfx.fill(barX, barY, barX + fillW, barY + 2, 0xFF00FF00.toInt())
        }

        // Border
        gfx.renderOutline(barX - 1, barY - 1, barW + 2, barH + 2, Theme.GRAY_DARK)

        // Progress text
        val progressText =
            if (status.speed.isNotEmpty()) {
                "${(status.progress * 100).toInt()}% • ${status.speed}"
            } else {
                "${(status.progress * 100).toInt()}%"
            }
        gfx.drawString(font, progressText, dims.x - font.width(progressText) / 2, barY + 10, Theme.GRAY_LIGHT, false)
    }

    private fun renderStatusMessage(
        gfx: GuiGraphics,
        dims: CardDimensions,
        status: BinStatusManager.InstallationStatus,
    ) {
        val (message, color) =
            when {
                status.isComplete -> {
                    ModLang
                        .translate("gui.library_setup.status.lib_ready_to_use")
                        .component()
                        .getString(128) to Theme.GREEN_LIGHT
                }

                status.isFailed -> {
                    ModLang
                        .translate("gui.library_setup.status.lib_failed_to_install")
                        .component()
                        .getString(128) to Theme.RED_LIGHT
                }

                else -> {
                    ModLang
                        .translate("gui.library_setup.status.lib_waiting_to_install")
                        .component()
                        .getString(128) to Theme.GRAY_LIGHT
                }
            }

        val messageY = dims.top + 8 + font.lineHeight + 3
        gfx.drawString(font, message, dims.x - font.width(message) / 2, messageY, color, false)
    }

    private fun renderSkipped(gfx: GuiGraphics) {
        val cx = width / 2
        val cy = height / 2
        val boxW = 460
        val topY = cy - 80
        val maxTextWidth = boxW - 40
        val padding = 12

        val notice =
            ModLang
                .translate("gui.library_setup.manual_install")
                .component()
                .toMultilineFormattedCharSequence(font, maxTextWidth)

        val boxH = padding + font.lineHeight + padding + (notice.size * font.lineHeight) + 5 + padding

        // Background
        gfx.fill(cx - boxW / 2, topY, cx + boxW / 2, topY + boxH, Theme.WARNING)
        gfx.renderOutline(cx - boxW / 2, topY, boxW, boxH, 0xFFFF5555.toInt())

        // Title
        val title =
            ModLang
                .translate("gui.library_setup.manual_install_title")
                .component()
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
        gfx.drawString(font, title, cx - font.width(title) / 2, topY + padding, 0xFFFFFF, false)

        var textY = topY + padding + font.lineHeight + padding
        notice.forEach { line ->
            if (line != FormattedCharSequence.EMPTY) {
                gfx.drawString(font, line, cx - font.width(line) / 2, textY, 0xDDDDDD, false)
            }
            textY += font.lineHeight
        }
    }

    // Helper method for wrapped text
    private fun renderWrappedText(
        gfx: GuiGraphics,
        text: Component,
        cx: Int,
        startY: Int,
        maxWidth: Int,
        color: Int,
    ) {
        font.split(text, maxWidth).forEachIndexed { index, line ->
            gfx.drawCenteredString(font, line, cx, startY + index * font.lineHeight, color)
        }
    }

    override fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
        button: Int,
    ): Boolean {
        if (currentState == State.DISCLAIMER && button == 0) {
            hoveredCardIndex?.let { index ->
                if (index in libraries.indices) {
                    Util.getPlatform().openUri(libraries[index].url)
                    return true
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun onClose() {
        minecraft?.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = true

    override fun shouldCloseOnEsc(): Boolean = !BinStatusManager.isAnyInstalling()

    private fun formatStatus(status: BinStatusManager.Status): String =
        when (status) {
            BinStatusManager.Status.PENDING -> "gui.library_setup.status.single_lib.pending"
            BinStatusManager.Status.NOT_INSTALLED -> "gui.library_setup.status.single_lib.not_installed"
            BinStatusManager.Status.DOWNLOADING -> "gui.library_setup.status.single_lib.downloading"
            BinStatusManager.Status.EXTRACTING -> "gui.library_setup.status.single_lib.extracting"
            BinStatusManager.Status.INSTALLED -> "gui.library_setup.status.single_lib.installed"
            BinStatusManager.Status.ALREADY_INSTALLED -> "gui.library_setup.status.single_lib.already_installed"
            BinStatusManager.Status.FAILED -> "gui.library_setup.status.single_lib.failed"
            BinStatusManager.Status.ERROR -> "gui.library_setup.status.single_lib.error"
        }.let { ModLang.translate(it).component().getString(128) }

    override fun tick() {
        super.tick()
        if (currentState == State.STATUS) {
            // Check if any installation state has changed
            var stateChanged = false

            BinStatusManager.LibraryType.entries.forEach { library ->
                val currentStatus = BinStatusManager.getStatus(library)
                val previousStatus = previousInstallationStates[library]

                // Detect state changes
                if (previousStatus == null ||
                    previousStatus.status != currentStatus.status ||
                    previousStatus.isInstalling != currentStatus.isInstalling ||
                    previousStatus.isComplete != currentStatus.isComplete
                ) {
                    stateChanged = true
                }

                previousInstallationStates[library] = currentStatus
            }

            // Rebuild if state changed or if widgets are empty or if installing
            if (stateChanged || children().isEmpty() || BinStatusManager.isAnyInstalling()) {
                rebuildWidgets()
            }
        }
    }
}
