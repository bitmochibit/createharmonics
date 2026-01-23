package me.mochibit.createharmonics.audio.bin

import net.minecraft.client.Minecraft
import java.io.File

abstract class BinProvider(
    private val providerName: String,
    val directory: File =
        Minecraft
            .getInstance()
            .gameDirectory
            .toPath()
            .resolve("audio_providers/$providerName")
            .toFile(),
) {
    companion object {
        private val OS_NAME = System.getProperty("os.name").lowercase()
        private val OS_ARCH = System.getProperty("os.arch").lowercase()

        val isWindows: Boolean = OS_NAME.contains("win")
        val isMac: Boolean = OS_NAME.contains("mac") || OS_NAME.contains("darwin")
        val isLinux: Boolean = OS_NAME.contains("linux")
        val isArm: Boolean = OS_ARCH.contains("aarch64") || OS_ARCH.contains("arm")
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
    internal fun clearCache() {
        cachedExecutablePath = null
    }

    private fun getExecutableName(): String = if (isWindows) "$providerName.exe" else providerName

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
