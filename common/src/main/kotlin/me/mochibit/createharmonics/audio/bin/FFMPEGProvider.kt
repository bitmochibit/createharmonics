package me.mochibit.createharmonics.audio.bin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

object FFMPEGProvider : BinProvider("ffmpeg") {
    @Volatile
    private var cachedFfprobePath: String? = null
    private var ffprobeChecked = false

    val ffprobePath: String?
        get() {
            if (ffprobeChecked) {
                cachedFfprobePath?.let { cached ->
                    if (java.io.File(cached).exists()) return cached
                }
            }
            val ffmpegPath =
                getExecutablePath() ?: run {
                    ffprobeChecked = true
                    cachedFfprobePath = null
                    return null
                }
            val probeName = if (isWindows) "ffprobe.exe" else "ffprobe"
            val probe = java.io.File(java.io.File(ffmpegPath).parentFile, probeName)
            ffprobeChecked = true
            cachedFfprobePath =
                if (probe.exists()) {
                    ensureExecutable(probe)
                    probe.absolutePath
                } else {
                    null
                }
            return cachedFfprobePath
        }

    fun isProbeAvailable(): Boolean {
        val execPath = ffprobePath
        return execPath != null && File(execPath).let { it.exists() && it.canExecute() }
    }

    override fun clearCache() {
        super.clearCache()
        cachedFfprobePath = null
        ffprobeChecked = false
    }

    override fun getDownloadUrl(): String =
        when {
            isWindows -> {
                fetchLatestUrl(variant = "win64-lgpl", extension = "zip")
            }

            isMac -> {
                "https://evermeet.cx/ffmpeg/getrelease/ffmpeg/zip"
            }

            isLinux -> {
                fetchLatestUrl(
                    variant = if (isArm) "linuxarm64-lgpl" else "linux64-lgpl",
                    extension = "tar.xz",
                )
            }

            else -> {
                throw UnsupportedOperationException("Unsupported OS: ${System.getProperty("os.name")}")
            }
        }

    private fun fetchLatestUrl(
        variant: String,
        extension: String,
    ): String {
        val client = HttpClient.newHttpClient()

        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create("https://api.github.com/repos/BtbN/FFmpeg-Builds/releases/tags/latest"))
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build()

        val body = client.send(request, HttpResponse.BodyHandlers.ofString()).body()
        val json = Json.parseToJsonElement(body).jsonObject

        val assets =
            json["assets"]?.jsonArray
                ?: error("No assets found in the latest release")

        val asset =
            assets.firstOrNull { element ->
                val name = element.jsonObject["name"]?.jsonPrimitive?.content ?: ""
                name.contains(variant) && name.endsWith(".$extension")
            } ?: error("No asset found for variant='$variant', extension='$extension'")

        return asset.jsonObject["browser_download_url"]!!.jsonPrimitive.content
    }
}
