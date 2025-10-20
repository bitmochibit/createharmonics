package me.mochibit.createharmonics.audio.binProvider

import me.mochibit.createharmonics.Logger.err
import me.mochibit.createharmonics.Logger.info
import net.minecraft.client.Minecraft
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.nio.channels.Channels
import java.util.zip.ZipInputStream
import java.util.concurrent.ConcurrentHashMap

abstract class AbstractDownloadableBinProvider(
    private val providerName: String,
    val directory: File = Minecraft.getInstance().gameDirectory.toPath()
        .resolve("audio_providers/$providerName").toFile(),
) {

    companion object {
        private val installationLocks = ConcurrentHashMap<String, Any>()

        private val OS_NAME = System.getProperty("os.name").lowercase()
        private val OS_ARCH = System.getProperty("os.arch").lowercase()

        val isWindows: Boolean = OS_NAME.contains("win")
        val isMac: Boolean = OS_NAME.contains("mac") || OS_NAME.contains("darwin")
        val isLinux: Boolean = OS_NAME.contains("linux")
        val isArm: Boolean = OS_ARCH.contains("aarch64") || OS_ARCH.contains("arm")
    }

    @Volatile
    private var cachedExecutablePath: String? = null

    fun isAvailable(): Boolean {
        val execPath = getExecutablePath()
        return execPath != null && File(execPath).let { it.exists() && it.canExecute() }
    }

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

    private fun getExecutableName(): String {
        return if (isWindows) "$providerName.exe" else providerName
    }

    private fun findExecutable(dir: File, exeName: String): File? {
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

    private fun ensureExecutable(file: File) {
        if (!file.canExecute()) {
            file.setExecutable(true, false)
        }
    }

    fun install(): Boolean {
        val lockObject = installationLocks.computeIfAbsent(providerName) { Any() }

        synchronized(lockObject) {
            if (isAvailable()) {
                info("$providerName is already installed")
                return true
            }

            try {
                info("Installing $providerName...")
                directory.mkdirs()

                val downloadUrl = getDownloadUrl()
                info("Downloading from: $downloadUrl")

                val tempFile = File.createTempFile("${providerName}_download", ".tmp")
                try {
                    downloadFile(downloadUrl, tempFile)

                    if (tempFile.length() == 0L) {
                        throw IOException("Downloaded file is empty")
                    }

                    extractDownloadedFile(downloadUrl, tempFile)

                    info("$providerName installed successfully")

                    // Clear cache and verify
                    cachedExecutablePath = null
                    return isAvailable().also { available ->
                        if (!available) {
                            err("Installation verification failed for $providerName")
                        }
                    }
                } finally {
                    tempFile.delete()
                }
            } catch (e: Exception) {
                err("Failed to install $providerName: ${e.message}")
                e.printStackTrace()
                return false
            }
        }
    }

    private fun extractDownloadedFile(downloadUrl: String, tempFile: File) {
        when {
            downloadUrl.endsWith(".zip") -> extractZip(tempFile, directory)
            downloadUrl.endsWith(".tar.xz") -> extractTarXz(tempFile, directory)
            else -> extractSingleFile(tempFile)
        }
    }

    private fun extractSingleFile(tempFile: File) {
        val exeName = getExecutableName()
        val targetFile = File(directory, exeName)
        tempFile.copyTo(targetFile, overwrite = true)
        ensureExecutable(targetFile)
    }

    protected abstract fun getDownloadUrl(): String

    private fun downloadFile(url: String, destination: File) {
        URL(url).openStream().use { input ->
            Channels.newChannel(input).use { rbc ->
                FileOutputStream(destination).use { output ->
                    output.channel.transferFrom(rbc, 0, Long.MAX_VALUE)
                }
            }
        }
    }

    private fun extractZip(zipFile: File, destDir: File) {
        // Ensure destination directory exists
        destDir.mkdirs()
        val destPath = destDir.canonicalFile.toPath()

        ZipInputStream(zipFile.inputStream()).use { zis ->
            generateSequence { zis.nextEntry }
                .forEach { entry ->
                    // Security: Prevent zip slip vulnerability by checking path before creating file
                    val entryPath = File(entry.name).toPath().normalize()
                    val resolvedPath = destPath.resolve(entryPath).normalize()

                    if (!resolvedPath.startsWith(destPath)) {
                        throw SecurityException("Zip entry is outside target directory: ${entry.name}")
                    }

                    val file = resolvedPath.toFile()

                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        file.outputStream().use { output ->
                            zis.copyTo(output)
                        }

                        // Make executable if it's a binary (no extension or .exe)
                        if (shouldBeExecutable(entry.name)) {
                            ensureExecutable(file)
                        }
                    }

                    zis.closeEntry()
                }
        }
    }

    private fun shouldBeExecutable(fileName: String): Boolean {
        val name = File(fileName).name
        return when {
            isWindows -> name.endsWith(".exe", ignoreCase = true)
            else -> !name.contains(".") || name == providerName
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun extractTarXz(tarXzFile: File, destDir: File) {
        throw UnsupportedOperationException(
            "tar.xz extraction not implemented yet. Please install $providerName manually."
        )
    }
}