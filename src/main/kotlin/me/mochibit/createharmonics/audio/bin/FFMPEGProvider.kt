package me.mochibit.createharmonics.audio.bin

object FFMPEGProvider : BinProvider(
    "ffmpeg",
) {
    val ffprobePath: String? by lazy {
        val ffmpegPath = getExecutablePath() ?: return@lazy null
        val ffmpegFile = java.io.File(ffmpegPath)
        val probeName = if (isWindows) "ffprobe.exe" else "ffprobe"
        val probe = java.io.File(ffmpegFile.parentFile, probeName)
        return@lazy if (probe.exists()) {
            ensureExecutable(probe)
            probe.absolutePath
        } else {
            null
        }
    }

    override fun getDownloadUrl(): String =
        when {
            // Windows static builds from BtbN's FFmpeg-Builds (official source mentioned on ffmpeg.org)
            isWindows -> {
                "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-n8.0-latest-win64-lgpl-8.0.zip"
            }

            isMac -> {
                // macOS - use static builds from evermeet.cx (official source mentioned on ffmpeg.org)
                "https://evermeet.cx/ffmpeg/getrelease/ffmpeg/zip"
            }

            isLinux -> {
                // Linux static builds from BtbN's FFmpeg-Builds (official source mentioned on ffmpeg.org)
                if (isArm) {
                    "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-n8.0-latest-linuxarm64-lgpl-8.0.tar.xz"
                } else {
                    "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-n8.0-latest-linux64-lgpl-8.0.tar.xz"
                }
            }

            else -> {
                throw UnsupportedOperationException("Unsupported OS: ${System.getProperty("os.name")}")
            }
        }
}
