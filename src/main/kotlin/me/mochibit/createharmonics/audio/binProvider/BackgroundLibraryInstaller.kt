package me.mochibit.createharmonics.audio.binProvider

import kotlinx.coroutines.Dispatchers
import me.mochibit.createharmonics.Logger
import me.mochibit.createharmonics.coroutine.launchModCoroutine
import me.mochibit.createharmonics.registry.ModLang
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

    enum class Status {
        PENDING,
        NOT_INSTALLED,
        DOWNLOADING,
        EXTRACTING,
        INSTALLED,
        ALREADY_INSTALLED,
        FAILED,
        ERROR,
    }

    data class InstallationStatus(
        val library: LibraryType,
        val isInstalling: Boolean = false,
        val isComplete: Boolean = false,
        val isFailed: Boolean = false,
        val progress: Float = 0.0f,
        val status: Status = Status.PENDING,
        val speed: String = "",
    )

    private val installationStatuses = ConcurrentHashMap<LibraryType, InstallationStatus>()
    private val installationInProgress = AtomicBoolean(false)

    init {
        // Initialize statuses
        LibraryType.entries.forEach { type ->
            val isInstalled = isLibraryInstalled(type)
            updateStatus(
                type,
                isInstalling = false,
                isComplete = isInstalled,
                status = if (isInstalled) Status.INSTALLED else Status.NOT_INSTALLED,
            )
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
                    updateStatus(LibraryType.YTDLP, isComplete = true, status = Status.ALREADY_INSTALLED)
                }

                // Install FFmpeg
                if (!isLibraryInstalled(LibraryType.FFMPEG)) {
                    installLibrary(LibraryType.FFMPEG)
                } else {
                    updateStatus(LibraryType.FFMPEG, isComplete = true, status = Status.ALREADY_INSTALLED)
                }
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
        updateStatus(library, isInstalling = true, status = Status.DOWNLOADING, progress = 0.0f)

        try {
            val provider =
                when (library) {
                    LibraryType.YTDLP -> YTDLProvider
                    LibraryType.FFMPEG -> FFMPEGProvider
                }

            val success =
                provider.install { statusString, progress, speed ->
                    val status =
                        when (statusString) {
                            "downloading" -> Status.DOWNLOADING
                            "extracting" -> Status.EXTRACTING
                            "completed" -> Status.INSTALLED
                            "already_installed" -> Status.ALREADY_INSTALLED
                            "failed" -> Status.FAILED
                            else -> Status.PENDING
                        }
                    updateStatus(
                        library,
                        isInstalling = true,
                        status = status,
                        progress = progress,
                        speed = speed,
                    )
                }

            if (success) {
                Logger.info("${library.displayName} installed successfully")
                updateStatus(library, isComplete = true, isFailed = false, status = Status.INSTALLED, progress = 1.0f)
                showToast(library, success = true)
            } else {
                Logger.err("${library.displayName} installation failed")
                updateStatus(library, isComplete = false, isFailed = true, status = Status.FAILED, progress = 0.0f)
                showToast(library, success = false)
            }
        } catch (e: Exception) {
            Logger.err("Error installing ${library.displayName}: ${e.message}")
            e.printStackTrace()
            updateStatus(library, isComplete = false, isFailed = true, status = Status.ERROR, progress = 0.0f)
            showToast(library, success = false)
        }
    }

    private fun updateStatus(
        library: LibraryType,
        isInstalling: Boolean = false,
        isComplete: Boolean = false,
        isFailed: Boolean = false,
        progress: Float = 0.0f,
        status: Status = Status.PENDING,
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
                        Component.literal(library.displayName).append(
                            ModLang.translate("library_installer.toast.success_title").component(),
                        ),
                        ModLang.translate("library_installer.toast.success_desc").component(),
                    ),
                )
            } else {
                toastManager.addToast(
                    SystemToast.multiline(
                        minecraft,
                        SystemToast.SystemToastIds.TUTORIAL_HINT,
                        Component.literal(library.displayName).append(
                            ModLang.translate("library_installer.toast.failure_title").component(),
                        ),
                        ModLang.translate("library_installer.toast.failure_desc").component(),
                    ),
                )
            }
        }
    }
}
