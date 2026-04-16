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

    private val availabilityCache = ConcurrentHashMap<LibraryType, Boolean>()
    private var lastAvailabilityCheck = 0L
    private const val AVAILABILITY_CACHE_TTL_MS = 2000L

    init {
        LibraryType.entries.forEach { type ->
            installationStatuses[type] = InstallationStatus(type, status = Status.PENDING)
        }
    }

    suspend fun initialize() {
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
        installationStatuses.remove(library)
        availabilityCache.remove(library)
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
        val currentStatus = installationStatuses[library]
        if (currentStatus != null) {
            val isInstalled = isLibraryInstalledCached(library)
            if (isInstalled && !currentStatus.isComplete) {
                updateStatus(library, progress = 1.0f, status = Status.INSTALLED, speed = "")
            } else if (!isInstalled && currentStatus.status == Status.INSTALLED) {
                updateStatus(library, progress = 0.0f, status = Status.NOT_INSTALLED, speed = "")
            }
        }

        return installationStatuses.getOrDefault(
            library,
            InstallationStatus(
                library,
                status = if (isLibraryInstalledCached(library)) Status.INSTALLED else Status.NOT_INSTALLED,
            ),
        )
    }

    fun isInstalling(library: LibraryType): Boolean = getStatus(library).isInstalling

    fun isAnyInstalling(): Boolean = installationStatuses.values.any { it.isInstalling }

    fun isAnyInstalled(): Boolean = LibraryType.entries.any { isLibraryInstalled(it) }

    private fun isLibraryInstalledCached(library: LibraryType): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastAvailabilityCheck > AVAILABILITY_CACHE_TTL_MS) {
            LibraryType.entries.forEach { type ->
                availabilityCache[type] = type.provider.isAvailable()
            }
            lastAvailabilityCheck = now
        }
        return availabilityCache[library] ?: isLibraryInstalled(library)
    }

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
