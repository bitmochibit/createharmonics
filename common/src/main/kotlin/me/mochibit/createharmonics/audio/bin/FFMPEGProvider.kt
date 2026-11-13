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

/**
 * Ffmpeg provider
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new FFMPEGProvider
 */
object FFMPEGProvider : BinProvider("ffmpeg") {
    @Volatile
    private var cachedFfprobePath: String? = null
    private var ffprobeChecked = false

    val ffprobePath: String?
        get() {
            if (ffprobeChecked) {
                cachedFfprobePath?.let { cached ->
                    if (File(cached).exists()) return cached
                }
            }
            ffprobeChecked = true
            cachedFfprobePath = findBinary("ffprobe")?.absolutePath
            return cachedFfprobePath
        }

    /**
     * Returns `true` if probe available
     *
     * @return `true` if the condition holds, `false` otherwise
     */
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
                if (isMacSilicon) {
                    throw LibraryDownloadUrlUnavailable(
                        "No static FFmpeg builds available for Apple Silicon. " +
                            "Please install ffmpeg manually (e.g. via Homebrew: brew install ffmpeg) " +
                            "and place it in ${directory.absolutePath}",
                    )
                }
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


    private val httpClient: HttpClient by lazy {
        HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }
    private fun fetchLatestUrl(
        variant: String,
        extension: String,
    ): String {
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create("https://api.github.com/repos/FFmpeg-Builds/FFmpeg-Builds/releases/tags/latest"))
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) {
            "GitHub API returned HTTP ${response.statusCode()}: ${response.body().take(200)}"
        }

        val assets =
            Json
                .parseToJsonElement(response.body())
                .jsonObject["assets"]
                ?.jsonArray
                ?: error("No assets found in the latest release")

        val asset =
            assets.firstOrNull {
                it.jsonObject["name"]?.jsonPrimitive?.content?.endsWith("-$variant.$extension") == true
            } ?: error("No asset found for variant='$variant', extension='$extension'")

        return asset.jsonObject["browser_download_url"]!!.jsonPrimitive.content
    }
}
