package me.mochibit.createharmonics.audio.binProvider

object FFMPEGProvider: DownloadableBinProvider(
    "ffmpeg"
) {
    override fun getDownloadUrl(): String {
        return when {
            isWindows -> {
                "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-n8.0-latest-win64-lgpl-8.0.zip"
            }
            isMac -> {
                // macOS - use static builds from evermeet.cx
                "https://evermeet.cx/ffmpeg/getrelease/ffmpeg/zip"
            }
            isLinux -> {
                // Linux static builds
                if (isArm) {
                    "https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-arm64-static.tar.xz"
                } else {
                    "https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz"
                }
            }
            else -> throw UnsupportedOperationException("Unsupported OS: ${System.getProperty("os.name")}")
        }
    }
}