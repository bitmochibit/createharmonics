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

    fun install(progressCallback: ((String, Float, String) -> Unit)? = null): Boolean {
        val lockObject = installationLocks.computeIfAbsent(providerName) { Any() }

        synchronized(lockObject) {
            if (isAvailable()) {
                info("$providerName is already installed")
                progressCallback?.invoke("already_installed", 1.0f, "")
                return true
            }

            try {
                info("Installing $providerName...")
                progressCallback?.invoke("downloading", 0.0f, "")
                directory.mkdirs()

                val downloadUrl = getDownloadUrl()
                info("Downloading from: $downloadUrl")

                val tempFile = File.createTempFile("${providerName}_download", ".tmp")
                try {
                    downloadFile(downloadUrl, tempFile, progressCallback)

                    if (tempFile.length() == 0L) {
                        throw IOException("Downloaded file is empty")
                    }

                    progressCallback?.invoke("extracting", 0.0f, "")
                    extractDownloadedFile(downloadUrl, tempFile)

                    info("$providerName installed successfully")
                    progressCallback?.invoke("completed", 1.0f, "")

                    // Clear cache and verify
                    cachedExecutablePath = null
                    return isAvailable().also { available ->
                        if (!available) {
                            err("Installation verification failed for $providerName")
                            progressCallback?.invoke("failed", 0.0f, "")
                        }
                    }
                } finally {
                    tempFile.delete()
                }
            } catch (e: Exception) {
                err("Failed to install $providerName: ${e.message}")
                e.printStackTrace()
                progressCallback?.invoke("failed", 0.0f, "")
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

    private fun downloadFile(url: String, destination: File, progressCallback: ((String, Float, String) -> Unit)? = null) {
        val connection = URL(url).openConnection()
        connection.connectTimeout = 30000 // 30 seconds
        connection.readTimeout = 30000 // 30 seconds

        val contentLength = connection.contentLengthLong

        connection.getInputStream().use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytes = 0L
                val startTime = System.currentTimeMillis()
                var lastUpdateTime = startTime
                var lastUpdateBytes = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead

                    val currentTime = System.currentTimeMillis()
                    val timeSinceLastUpdate = currentTime - lastUpdateTime

                    // Update progress every 100ms
                    if (timeSinceLastUpdate >= 100) {
                        val bytesSinceLastUpdate = totalBytes - lastUpdateBytes
                        val speedBytesPerSec = (bytesSinceLastUpdate * 1000.0 / timeSinceLastUpdate).toLong()
                        val speedText = formatSpeed(speedBytesPerSec)

                        val progress = if (contentLength > 0) {
                            totalBytes.toFloat() / contentLength.toFloat()
                        } else {
                            0.0f
                        }

                        progressCallback?.invoke("downloading", progress, speedText)

                        lastUpdateTime = currentTime
                        lastUpdateBytes = totalBytes

                        // Log progress every 1MB
                        if (totalBytes % (1024 * 1024) < buffer.size) {
                            info("Downloaded ${totalBytes / (1024 * 1024)} MB... ($speedText)")
                        }
                    }
                }

                info("Download complete: ${totalBytes / (1024 * 1024)} MB")
            }
        }
    }

    private fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond >= 1024 * 1024 -> "%.2f MB/s".format(bytesPerSecond / (1024.0 * 1024.0))
            bytesPerSecond >= 1024 -> "%.2f KB/s".format(bytesPerSecond / 1024.0)
            else -> "$bytesPerSecond B/s"
        }
    }

    private fun extractZip(zipFile: File, destDir: File) {
        // Ensure destination directory exists
        destDir.mkdirs()
        val destPath = destDir.canonicalFile.toPath()

        info("Extracting zip archive...")
        var fileCount = 0

        ZipInputStream(zipFile.inputStream()).use { zis ->
            generateSequence { zis.nextEntry }
                .forEach { entry ->
                    fileCount++
                    if (fileCount % 10 == 0) {
                        info("Extracted $fileCount files...")
                    }

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

        info("Extraction complete: $fileCount files extracted")
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