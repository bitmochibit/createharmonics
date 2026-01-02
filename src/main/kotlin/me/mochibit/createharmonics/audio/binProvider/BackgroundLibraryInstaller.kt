package me.mochibit.createharmonics.audio.binProvider

import kotlinx.coroutines.Dispatchers
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.coroutine.launchModCoroutine
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.network.chat.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object BackgroundLibraryInstaller {
    enum class LibraryType(
        val displayName: String,
    ) {
        YTDLP("yt-dlp"),
        FFMPEG("FFmpeg"),
    }

    data class InstallationStatus(
        val library: LibraryType,
        val isInstalling: Boolean = false,
        val isComplete: Boolean = false,
        val isFailed: Boolean = false,
        val progress: Float = 0.0f,
        val status: String = "Pending",
        val speed: String = "",
    )

    private val installationStatuses = ConcurrentHashMap<LibraryType, InstallationStatus>()
    private val installationInProgress = AtomicBoolean(false)

    init {
        // Initialize statuses
        LibraryType.entries.forEach { type ->
            updateStatus(type, isInstalling = false, isComplete = isLibraryInstalled(type))
        }
    }

    /**
     * Check if a specific library is installed
     */
    fun isLibraryInstalled(library: LibraryType): Boolean =
        when (library) {
            LibraryType.YTDLP -> YTDLProvider.isAvailable()
            LibraryType.FFMPEG -> FFMPEGProvider.isAvailable()
        }

    /**
     * Check if all libraries are installed
     */
    fun areAllLibrariesInstalled(): Boolean = LibraryType.entries.all { isLibraryInstalled(it) }

    /**
     * Check if installation is currently in progress
     */
    fun isInstalling(): Boolean = installationInProgress.get()

    /**
     * Get current installation status for a library
     */
    fun getStatus(library: LibraryType): InstallationStatus =
        installationStatuses.getOrDefault(
            library,
            InstallationStatus(library, isComplete = isLibraryInstalled(library)),
        )

    /**
     * Get all installation statuses
     */
    fun getAllStatuses(): Map<LibraryType, InstallationStatus> = installationStatuses.toMap()

    /**
     * Start background installation of missing libraries
     */
    fun startBackgroundInstallation() {
        if (!installationInProgress.compareAndSet(false, true)) {
            Logger.warn("Library installation already in progress")
            return
        }

        Logger.info("Starting background library installation...")

        launchModCoroutine(Dispatchers.IO) {
            try {
                // Install yt-dlp
                if (!isLibraryInstalled(LibraryType.YTDLP)) {
                    installLibrary(LibraryType.YTDLP)
                } else {
                    updateStatus(LibraryType.YTDLP, isComplete = true, status = "Already Installed")
                }

                // Install FFmpeg
                if (!isLibraryInstalled(LibraryType.FFMPEG)) {
                    installLibrary(LibraryType.FFMPEG)
                } else {
                    updateStatus(LibraryType.FFMPEG, isComplete = true, status = "Already Installed")
                }

                Logger.info("Background library installation completed")
            } catch (e: Exception) {
                Logger.err("Background installation failed: ${e.message}")
                e.printStackTrace()
            } finally {
                installationInProgress.set(false)
            }
        }
    }

    private fun installLibrary(library: LibraryType) {
        Logger.info("Installing ${library.displayName}...")
        updateStatus(library, isInstalling = true, status = "Downloading", progress = 0.0f)

        try {
            val provider =
                when (library) {
                    LibraryType.YTDLP -> YTDLProvider
                    LibraryType.FFMPEG -> FFMPEGProvider
                }

            val success =
                provider.install { status, progress, speed ->
                    updateStatus(
                        library,
                        isInstalling = true,
                        status = formatStatus(status),
                        progress = progress,
                        speed = speed,
                    )
                }

            if (success) {
                Logger.info("${library.displayName} installed successfully")
                updateStatus(library, isComplete = true, isFailed = false, status = "Installed", progress = 1.0f)
                showToast(library, success = true)
            } else {
                Logger.err("${library.displayName} installation failed")
                updateStatus(library, isComplete = false, isFailed = true, status = "Failed", progress = 0.0f)
                showToast(library, success = false)
            }
        } catch (e: Exception) {
            Logger.err("Error installing ${library.displayName}: ${e.message}")
            e.printStackTrace()
            updateStatus(library, isComplete = false, isFailed = true, status = "Error", progress = 0.0f)
            showToast(library, success = false)
        }
    }

    private fun updateStatus(
        library: LibraryType,
        isInstalling: Boolean = false,
        isComplete: Boolean = false,
        isFailed: Boolean = false,
        progress: Float = 0.0f,
        status: String = "Pending",
        speed: String = "",
    ) {
        installationStatuses[library] =
            InstallationStatus(
                library = library,
                isInstalling = isInstalling,
                isComplete = isComplete,
                isFailed = isFailed,
                progress = progress,
                status = status,
                speed = speed,
            )
    }

    private fun formatStatus(raw: String): String =
        when (raw) {
            "downloading" -> "Downloading"
            "extracting" -> "Extracting"
            "completed" -> "Completed"
            "already_installed" -> "Already Installed"
            "failed" -> "Failed"
            else -> raw.replaceFirstChar { it.uppercase() }
        }

    private fun showToast(
        library: LibraryType,
        success: Boolean,
    ) {
        Minecraft.getInstance().execute {
            val minecraft = Minecraft.getInstance()
            val toastManager = minecraft.toasts

            if (success) {
                toastManager.addToast(
                    SystemToast.multiline(
                        minecraft,
                        SystemToast.SystemToastIds.TUTORIAL_HINT,
                        Component.literal("${library.displayName} Ready"),
                        Component.literal("${library.displayName} has been installed successfully!"),
                    ),
                )
            } else {
                toastManager.addToast(
                    SystemToast.multiline(
                        minecraft,
                        SystemToast.SystemToastIds.TUTORIAL_HINT,
                        Component.literal("${library.displayName} Failed"),
                        Component.literal("Failed to install ${library.displayName}. Check logs."),
                    ),
                )
            }
        }
    }
}
