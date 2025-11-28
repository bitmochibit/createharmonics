package me.mochibit.createharmonics.client.audio.binProvider


object YTDLProvider: DownloadableBinProvider(
    "yt-dlp"
) {
    override fun getDownloadUrl(): String {
        return when {
            isWindows -> {
                "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"
            }
            isMac -> {
                "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_macos"
            }
            isLinux -> {
                if (isArm) {
                    "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux_aarch64"
                } else {
                    "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux"
                }
            }
            else -> throw UnsupportedOperationException("Unsupported OS: ${System.getProperty("os.name")}")
        }
    }
}