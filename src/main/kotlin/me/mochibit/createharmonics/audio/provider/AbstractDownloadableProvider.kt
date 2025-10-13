package me.mochibit.createharmonics.audio.provider

import me.mochibit.createharmonics.CreateHarmonics
import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.Logger.err
import me.mochibit.createharmonics.Logger.info
import net.minecraft.client.Minecraft
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.channels.Channels
import java.util.zip.ZipInputStream

abstract class AbstractDownloadableProvider(
    private val providerName: String,
    val directory: File = Minecraft.getInstance().gameDirectory.toPath().resolve("/audio_providers/$providerName").toFile(), // Default to mod directory
) {

    private var cachedExecutablePath: String? = null

    fun getName(): String = providerName

    fun isAvailable(): Boolean {
        val execPath = getExecutablePath()
        return execPath != null && File(execPath).exists()
    }

    fun getExecutablePath(): String? {
        if (cachedExecutablePath != null && File(cachedExecutablePath!!).exists()) {
            return cachedExecutablePath
        }

        if (!directory.exists()) {
            return null
        }

        // Find executable in directory
        val exeName = if (System.getProperty("os.name").lowercase().contains("win")) {
            "$providerName.exe"
        } else {
            providerName
        }

        val executable = findExecutable(directory, exeName)
        cachedExecutablePath = executable?.absolutePath
        return cachedExecutablePath
    }

    private fun findExecutable(dir: File, exeName: String): File? {
        if (!dir.isDirectory) return null

        val directExec = File(dir, exeName)
        if (directExec.exists() && directExec.canExecute()) {
            return directExec
        }

        // Search in subdirectories
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                val found = findExecutable(file, exeName)
                if (found != null) return found
            } else if (file.name.equals(exeName, ignoreCase = true)) {
                if (!file.canExecute()) {
                    file.setExecutable(true)
                }
                return file
            }
        }

        return null
    }

    fun install(): Boolean {
        if (isAvailable()) {
            info("$providerName is already installed")
            return true
        }

        try {
            info("Installing $providerName...")

            if (!directory.exists()) {
                directory.mkdirs()
            }

            val downloadUrl = getDownloadUrl()
            info("Downloading from: $downloadUrl")

            val tempFile = File.createTempFile("${providerName}_download", ".tmp")
            try {
                downloadFile(downloadUrl, tempFile)

                // Extract if it's a zip file
                if (downloadUrl.endsWith(".zip")) {
                    extractZip(tempFile, directory)
                } else {
                    // Copy directly
                    val targetFile = File(directory, tempFile.name)
                    tempFile.copyTo(targetFile, overwrite = true)
                    targetFile.setExecutable(true)
                }

                info("$providerName installed successfully")

                // Clear cache and verify
                cachedExecutablePath = null
                return isAvailable()
            } finally {
                tempFile.delete()
            }
        } catch (e: Exception) {
            err("Failed to install $providerName")
            return false
        }
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
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(destDir, entry.name)

                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    file.outputStream().use { output ->
                        zis.copyTo(output)
                    }

                    // Make executable if it's a binary
                    if (!entry.name.contains(".")) {
                        file.setExecutable(true)
                    }
                }

                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}