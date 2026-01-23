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
        val isInstalling: Boolean = false,
        val isComplete: Boolean = false,
        val isFailed: Boolean = false,
        val progress: Float = 0.0f,
        val status: Status = Status.PENDING,
        val speed: String = "",
    )

    private val installationStatuses = ConcurrentHashMap<LibraryType, InstallationStatus>()

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
    fun isLibraryInstalled(library: LibraryType): Boolean = library.provider.isAvailable()

    /**
     * Check if all libraries are installed
     */
    fun areAllLibrariesInstalled(): Boolean = LibraryType.entries.all { isLibraryInstalled(it) }

    /**
     * Get current installation status for a library
     */
    fun getStatus(library: LibraryType): InstallationStatus =
        installationStatuses.getOrDefault(
            library,
            InstallationStatus(library, isComplete = isLibraryInstalled(library)),
        )

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
}
