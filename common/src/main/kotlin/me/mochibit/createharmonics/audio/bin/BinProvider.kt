package me.mochibit.createharmonics.audio.bin

import me.mochibit.createharmonics.foundation.locale.ModLang
import net.minecraft.client.Minecraft
import java.io.File

class LibraryDownloadUrlUnavailable(
    message: String,
) : Exception(message)

abstract class BinProvider(
    private val providerName: String,
    val directory: File =
        Minecraft
            .getInstance()
            .gameDirectory
            .toPath()
            .resolve("audio_providers/$providerName")
            .normalize()
            .toFile(),
) {
    companion object {
        private val debugLinux = false

        private val OS_NAME =
            when {
                debugLinux -> "linux"
                else -> System.getProperty("os.name").lowercase()
            }

        private val OS_ARCH =
            when {
                debugLinux -> "aarch64"
                else -> System.getProperty("os.arch").lowercase()
            }

        val isWindows: Boolean = OS_NAME.contains("win")
        val isMac: Boolean = OS_NAME.contains("mac") || OS_NAME.contains("darwin")
        val isLinux: Boolean = OS_NAME.contains("linux")
        val isArm: Boolean = OS_ARCH.contains("aarch64") || OS_ARCH.contains("arm")
        val isMacSilicon: Boolean = isMac && isArm

        val providersFolder: File =
            Minecraft
                .getInstance()
                .gameDirectory
                .toPath()
                .resolve("audio_providers")
                .normalize()
                .toFile()
    }

    fun buildProviderFolder() {
        if (!providersFolder.exists()) {
            providersFolder.mkdirs()
        }

        if (!directory.exists()) {
            directory.mkdirs()
        }

        val instructionFile = File(directory, "$providerName-instructions.md")
        if (!instructionFile.exists() || instructionFile.readText().trim() != providerInstructions.trim()) {
            instructionFile.writeText(providerInstructions)
        }
    }

    val providerInstructions: String
        get() {
            val downloadUrl: String =
                try {
                    getDownloadUrl()
                } catch (e: LibraryDownloadUrlUnavailable) {
                    e.message ?: "No download URL was found for $providerName"
                }

            return ModLang
                .translate(
                    "audio.binary.instructions",
                    providerName,
                    downloadUrl,
                    directory.absoluteFile.path,
                ).string()
        }

    @Volatile
    private var cachedExecutablePath: String? = null

    /**
     * Check if the binary is available and executable
     */
    fun isAvailable(): Boolean {
        val execPath = getExecutablePath()
        return execPath != null && File(execPath).let { it.exists() && it.canExecute() }
    }

    /**
     * Get the path to the executable, if it exists
     */
    fun getExecutablePath(): String? {
        cachedExecutablePath?.let { cached ->
            if (File(cached).exists()) return cached
        }

        if (!directory.exists()) return null

        val exeName = getExecutableName()
        val executable = findExecutable(directory, exeName)
        cachedExecutablePath = executable?.absolutePath
        return cachedExecutablePath
    }

    /**
     * Clear the cached executable path (useful after installation)
     */
    internal open fun clearCache() {
        cachedExecutablePath = null
    }

    private fun getExecutableName(): String = if (isWindows) "$providerName.exe" else providerName

    internal fun findBinary(name: String): File? {
        val exeName = if (isWindows) "$name.exe" else name
        return findExecutable(directory, exeName)
    }

    private fun findExecutable(
        dir: File,
        exeName: String,
    ): File? {
        if (!dir.isDirectory) return null

        // Check direct path first
        val directExec = File(dir, exeName)
        if (directExec.exists()) {
            ensureExecutable(directExec)
            return directExec
        }

        // Search in subdirectories (breadth-first to prefer shallow paths)
        val queue = ArrayDeque<File>()
        queue.add(dir)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            current.listFiles()?.forEach { file ->
                if (file.name.equals(exeName, ignoreCase = true)) {
                    ensureExecutable(file)
                    return file
                }
                if (file.isDirectory) {
                    queue.add(file)
                }
            }
        }

        return null
    }

    internal fun ensureExecutable(file: File) {
        if (!file.canExecute()) {
            file.setExecutable(true, false)
        }
    }

    internal fun getExecutableNameInternal(): String = getExecutableName()

    /**
     * Get the download URL for this platform
     */
    abstract fun getDownloadUrl(): String
}
