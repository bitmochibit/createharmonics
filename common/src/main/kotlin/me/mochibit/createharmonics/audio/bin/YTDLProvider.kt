package me.mochibit.createharmonics.audio.bin

/**
 * Ytdl provider
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new YTDLProvider
 */
object YTDLProvider : BinProvider(
    "yt-dlp",
) {
    override fun getExecutableBaseName(): String = when {
        isMac -> providerName + "_macos"
        else -> providerName
    }

    override fun getDownloadUrl(): String =
        when {
            isWindows -> {
                "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"
            }

            isMac -> {
                "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_macos.zip"
            }

            isLinux -> {
                if (isArm) {
                    "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux_aarch64"
                } else {
                    "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux"
                }
            }

            else -> {
                throw UnsupportedOperationException("Unsupported OS: ${System.getProperty("os.name")}")
            }
        }
}
