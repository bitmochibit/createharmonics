package me.mochibit.createharmonics.audio.bin

import me.mochibit.createharmonics.Logger.err
import me.mochibit.createharmonics.Logger.info
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

object BinInstaller {
    private val installationLocks = ConcurrentHashMap<String, Any>()

    /**
     * Install a binary provider
     */
    fun install(
        provider: BinProvider,
        providerName: String,
        progressCallback: ((String, Float, String) -> Unit)? = null,
    ): Boolean {
        val lockObject = installationLocks.computeIfAbsent(providerName) { Any() }

        synchronized(lockObject) {
            if (provider.isAvailable()) {
                info("$providerName is already installed")
                progressCallback?.invoke("already_installed", 1.0f, "")
                return true
            }

            try {
                info("Installing $providerName...")
                progressCallback?.invoke("downloading", 0.0f, "")
                provider.directory.mkdirs()

                val downloadUrl = provider.getDownloadUrl()
                info("Downloading from: $downloadUrl")

                val tempFile = File.createTempFile("${providerName}_download", ".tmp")
                try {
                    downloadFile(downloadUrl, tempFile, progressCallback)

                    if (tempFile.length() == 0L) {
                        throw IOException("Downloaded file is empty")
                    }

                    progressCallback?.invoke("extracting", 0.0f, "")
                    extractDownloadedFile(downloadUrl, tempFile, provider)

                    info("$providerName installed successfully")
                    progressCallback?.invoke("completed", 1.0f, "")

                    // Clear cache and verify
                    provider.clearCache()
                    return provider.isAvailable().also { available ->
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

    private fun extractDownloadedFile(
        downloadUrl: String,
        tempFile: File,
        provider: BinProvider,
    ) {
        when {
            downloadUrl.endsWith(".zip") -> extractZip(tempFile, provider.directory, provider)
            downloadUrl.endsWith(".tar.xz") -> extractTarXz(tempFile, provider.directory)
            else -> extractSingleFile(tempFile, provider)
        }
    }

    private fun extractSingleFile(
        tempFile: File,
        provider: BinProvider,
    ) {
        val exeName = provider.getExecutableNameInternal()
        val targetFile = File(provider.directory, exeName)
        tempFile.copyTo(targetFile, overwrite = true)
        provider.ensureExecutable(targetFile)
    }

    private fun downloadFile(
        url: String,
        destination: File,
        progressCallback: ((String, Float, String) -> Unit)? = null,
    ) {
        val connection = URL(url).openConnection()
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

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

                    if (timeSinceLastUpdate >= 100) {
                        val bytesSinceLastUpdate = totalBytes - lastUpdateBytes
                        val speedBytesPerSec = (bytesSinceLastUpdate * 1000.0 / timeSinceLastUpdate).toLong()
                        val speedText = formatSpeed(speedBytesPerSec)

                        val progress =
                            if (contentLength > 0) {
                                totalBytes.toFloat() / contentLength.toFloat()
                            } else {
                                0.0f
                            }

                        progressCallback?.invoke("downloading", progress, speedText)

                        lastUpdateTime = currentTime
                        lastUpdateBytes = totalBytes

                        if (totalBytes % (1024 * 1024) < buffer.size) {
                            info("Downloaded ${totalBytes / (1024 * 1024)} MB... ($speedText)")
                        }
                    }
                }

                info("Download complete: ${totalBytes / (1024 * 1024)} MB")
            }
        }
    }

    private fun formatSpeed(bytesPerSecond: Long): String =
        when {
            bytesPerSecond >= 1024 * 1024 -> "%.2f MB/s".format(bytesPerSecond / (1024.0 * 1024.0))
            bytesPerSecond >= 1024 -> "%.2f KB/s".format(bytesPerSecond / 1024.0)
            else -> "$bytesPerSecond B/s"
        }

    private fun extractZip(
        zipFile: File,
        destDir: File,
        provider: BinProvider,
    ) {
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

                        if (shouldBeExecutable(entry.name, provider)) {
                            provider.ensureExecutable(file)
                        }
                    }

                    zis.closeEntry()
                }
        }

        info("Extraction complete: $fileCount files extracted")
    }

    private fun shouldBeExecutable(
        fileName: String,
        provider: BinProvider,
    ): Boolean {
        val name = File(fileName).name
        return when {
            BinProvider.isWindows -> name.endsWith(".exe", ignoreCase = true)
            else -> !name.contains(".") || name == provider.getExecutableNameInternal()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun extractTarXz(
        tarXzFile: File,
        destDir: File,
    ): Unit =
        throw UnsupportedOperationException(
            "tar.xz extraction not implemented yet. Please install manually.",
        )
}
