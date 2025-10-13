package me.mochibit.createharmonics.audio.provider

object FFMPEG: AbstractDownloadableProvider(
    "ffmpeg"
) {
    override fun getDownloadUrl(): String {
        val osName = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()

        return when {
            osName.contains("win") -> {
                // Windows builds from gyan.dev
                "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip"
            }
            osName.contains("mac") || osName.contains("darwin") -> {
                // macOS - use static builds
                if (arch.contains("aarch64") || arch.contains("arm")) {
                    "https://evermeet.cx/ffmpeg/getrelease/ffmpeg/zip"
                } else {
                    "https://evermeet.cx/ffmpeg/getrelease/ffmpeg/zip"
                }
            }
            osName.contains("linux") -> {
                // Linux static builds
                if (arch.contains("aarch64") || arch.contains("arm")) {
                    "https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-arm64-static.tar.xz"
                } else {
                    "https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz"
                }
            }
            else -> throw UnsupportedOperationException("Unsupported OS: $osName")
        }
    }
}