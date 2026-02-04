package me.mochibit.createharmonics.audio.bin

import java.util.concurrent.ConcurrentHashMap

object BinStatusManager {
    enum class LibraryType(
        val displayName: String,
        val provider: BinProvider,
    ) {
        YTDLP("yt-dlp", YTDLProvider),
        FFMPEG("FFmpeg", FFMPEGProvider),
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
        val progress: Float = 0.0f,
        val status: Status = Status.PENDING,
        val speed: String = "",
    ) {
        val isInstalling: Boolean
            get() = status == Status.DOWNLOADING || status == Status.EXTRACTING

        val isComplete: Boolean
            get() = status == Status.INSTALLED || status == Status.ALREADY_INSTALLED

        val isFailed: Boolean
            get() = status == Status.FAILED || status == Status.ERROR
    }

    private val installationStatuses = ConcurrentHashMap<LibraryType, InstallationStatus>()

    init {
        LibraryType.entries.forEach { type ->
            val isInstalled = isLibraryInstalled(type)
            type.provider.buildProviderFolder()
            updateStatus(
                type,
                status = if (isInstalled) Status.INSTALLED else Status.NOT_INSTALLED,
            )
        }
    }

    fun ensureBinaryFolders() {
        LibraryType.entries.forEach { type ->
            type.provider.buildProviderFolder()
        }
    }

    fun resetStatus(library: LibraryType) {
        // Delete the library from the status map to force re-evaluation
        installationStatuses.remove(library)

        // Re-initialize with fresh status check
        val isInstalled = isLibraryInstalled(library)
        updateStatus(
            library,
            progress = 0.0f,
            status = if (isInstalled) Status.INSTALLED else Status.NOT_INSTALLED,
            speed = "",
        )
    }

    /**
     * Check if a specific library is installed
     */
    fun isLibraryInstalled(library: LibraryType): Boolean = library.provider.isAvailable()

    /**
     * Check if all libraries are installed
     */
    fun areAllLibrariesInstalled(): Boolean = LibraryType.entries.all { isLibraryInstalled(it) }

    /**
     * Get current installation status for a library
     */
    fun getStatus(library: LibraryType): InstallationStatus {
        // Check if the status changed outside of this manager
        val currentStatus = installationStatuses[library]
        if (currentStatus != null) {
            val isInstalled = isLibraryInstalled(library)
            if (isInstalled && !currentStatus.isComplete) {
                // Update status to INSTALLED if it was installed externally
                updateStatus(
                    library,
                    progress = 1.0f,
                    status = Status.INSTALLED,
                    speed = "",
                )
            } else if (!isInstalled && currentStatus.status == Status.INSTALLED) {
                // Update status to NOT_INSTALLED if it was uninstalled externally
                updateStatus(
                    library,
                    progress = 0.0f,
                    status = Status.NOT_INSTALLED,
                    speed = "",
                )
            }
        }

        return installationStatuses.getOrDefault(
            library,
            InstallationStatus(
                library,
                status = if (isLibraryInstalled(library)) Status.INSTALLED else Status.NOT_INSTALLED,
            ),
        )
    }

    fun isInstalling(library: LibraryType): Boolean = getStatus(library).isInstalling

    fun isAnyInstalling(): Boolean = installationStatuses.values.any { it.isInstalling }

    /**
     * Get all installation statuses
     */
    fun getAllStatuses(): Map<LibraryType, InstallationStatus> = installationStatuses.toMap()

    /**
     * Update the status of a library (used by BackgroundLibraryInstaller)
     */
    internal fun updateStatus(
        library: LibraryType,
        progress: Float = 0.0f,
        status: Status = Status.PENDING,
        speed: String = "",
    ) {
        installationStatuses[library] =
            InstallationStatus(
                library = library,
                progress = progress,
                status = status,
                speed = speed,
            )
    }
}
