package me.mochibit.createharmonics.audio.binProvider

object FFMPEGBin: AbstractDownloadableBinProvider(
    "ffmpeg"
) {
    override fun getDownloadUrl(): String {
        return when {
            isWindows -> {
                // Windows builds from gyan.dev
                "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip"
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