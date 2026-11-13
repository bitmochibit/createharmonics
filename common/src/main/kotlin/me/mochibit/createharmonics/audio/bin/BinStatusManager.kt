package me.mochibit.createharmonics.audio.bin

import java.util.concurrent.ConcurrentHashMap

/**
 * Bin status manager
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new BinStatusManager
 */
object BinStatusManager {
    /**
     * Library type
     * Enum — a fixed set of named constants.
     *
     * @constructor Creates a new LibraryType
     * @property displayName the display name
     * @property provider
     */
    enum class LibraryType(
        val displayName: String,
        val provider: BinProvider,
    ) {
        /**
         * Ytdlp
         *
         * @constructor Creates a new YTDLP
         */
        YTDLP("yt-dlp", YTDLProvider),

        /**
         * Ffmpeg
         *
         * @constructor Creates a new FFMPEG
         */
        FFMPEG("FFmpeg", FFMPEGProvider),
    }

    /**
     * Status
     * Enum — a fixed set of named constants.
     *
     * @constructor Creates a new Status
     */
    enum class Status {
        /**
         * Pending
         *
         * @constructor Creates a new PENDING
         */
        PENDING,

        /**
         * Not installed
         *
         * @constructor Creates a new NOT_INSTALLED
         */
        NOT_INSTALLED,

        /**
         * Downloading
         *
         * @constructor Creates a new DOWNLOADING
         */
        DOWNLOADING,

        /**
         * Extracting
         *
         * @constructor Creates a new EXTRACTING
         */
        EXTRACTING,

        /**
         * Installed
         *
         * @constructor Creates a new INSTALLED
         */
        INSTALLED,

        /**
         * Already installed
         *
         * @constructor Creates a new ALREADY_INSTALLED
         */
        ALREADY_INSTALLED,

        /**
         * Failed
         *
         * @constructor Creates a new FAILED
         */
        FAILED,

        /**
         * Error
         *
         * @constructor Creates a new ERROR
         */
        ERROR,
    }

    /**
     * Installation status
     * Data class — a value holder; `equals`, `hashCode`, `toString` and `copy` are generated.
     *
     * @constructor Creates a new InstallationStatus
     * @property library
     * @property progress
     * @property status
     * @property speed
     */
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

    /**
     * Initialises
     * Suspending function — must be called from a coroutine or another suspending function.
     *
     */
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

    /**
     * Validates the binary folders
     *
     */
    fun ensureBinaryFolders() {
        LibraryType.entries.forEach { type ->
            type.provider.buildProviderFolder()
        }
    }

    /**
     * Resets the status
     *
     * @param library
     */
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
     *
     * @param library
     * @return `true` if the condition holds, `false` otherwise
     */
    fun isLibraryInstalled(library: LibraryType): Boolean = library.provider.isAvailable()

    /**
     * Check if all libraries are installed
     *
     * @return `true` if the condition holds, `false` otherwise
     */
    fun areAllLibrariesInstalled(): Boolean = LibraryType.entries.all { isLibraryInstalled(it) }

    /**
     * Get current installation status for a library
     *
     * @param library
     * @return the installation status
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

    /**
     * Returns `true` if installing
     *
     * @param library
     * @return `true` if the condition holds, `false` otherwise
     */
    fun isInstalling(library: LibraryType): Boolean = getStatus(library).isInstalling

    /**
     * Returns `true` if any installing
     *
     * @return `true` if the condition holds, `false` otherwise
     */
    fun isAnyInstalling(): Boolean = installationStatuses.values.any { it.isInstalling }

    /**
     * Returns `true` if any installed
     *
     * @return `true` if the condition holds, `false` otherwise
     */
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
     *
     * @return the map
     */
    fun getAllStatuses(): Map<LibraryType, InstallationStatus> = installationStatuses.toMap()

    /**
     * Update the status of a library (used by BackgroundLibraryInstaller)
     *
     * @param library
     * @param progress
     * @param status
     * @param speed
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
