package me.mochibit.createharmonics.audio.provider


object YTDL: AbstractDownloadableProvider(
    "yt-dlp"
) {
    override fun getDownloadUrl(): String {
        val osName = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()

        return when {
            osName.contains("win") -> {
                "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"
            }
            osName.contains("mac") || osName.contains("darwin") -> {
                "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_macos"
            }
            osName.contains("linux") -> {
                if (arch.contains("aarch64") || arch.contains("arm")) {
                    "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux_aarch64"
                } else {
                    "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux"
                }
            }
            else -> throw UnsupportedOperationException("Unsupported OS: $osName")
        }
    }
}