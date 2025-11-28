package me.mochibit.createharmonics.client.audio.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.client.audio.binProvider.FFMPEGProvider
import me.mochibit.createharmonics.coroutine.launchModCoroutine
import java.io.File
import java.io.InputStream


class FFmpegExecutor {
    companion object {
        private const val CHUNK_SIZE = 8192
    }

    private var process: Process? = null
    private var processId: Long? = null

    val inputStream: InputStream?
        get() = process?.inputStream

    val errorStream: InputStream?
        get() = process?.errorStream

    fun decodeFileToStream(audioFile: File, sampleRate: Int): Flow<ByteArray> = flow {
        val ffmpegPath = FFMPEGProvider.getExecutablePath()
            ?: throw IllegalStateException("FFmpeg not found")

        val command = listOf(
            ffmpegPath,
            "-i", audioFile.absolutePath,
            "-f", "s16le",
            "-ar", sampleRate.toString(),
            "-ac", "1",
            "-loglevel", "error",
            "pipe:1"
        )

        val process = ProcessBuilder(command).start()
        val processId = ProcessLifecycleManager.registerProcess(process)

        try {
            val buffer = ByteArray(CHUNK_SIZE)
            var totalBytes = 0L

            process.inputStream.use { input ->
                while (true) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) break

                    emit(buffer.copyOf(bytesRead))
                    totalBytes += bytesRead
                }
            }

            info("Decoded $totalBytes bytes of PCM from file")
        } finally {
            ProcessLifecycleManager.destroyProcess(processId)
        }
    }.flowOn(Dispatchers.IO)

    fun createStream(url: String, sampleRate: Int = 48000, seekOffset: Double = 0.0) {
        if (!FFMPEGProvider.isAvailable()) return
        if (isRunning()) return

        val ffmpegPath = FFMPEGProvider.getExecutablePath()

        val command = buildList {
            add(ffmpegPath)

            // Add seek offset before input for faster seeking
            if (seekOffset > 0.0) {
                add("-ss")
                add(seekOffset.toString())
            }

            add("-reconnect")
            add("1")
            add("-reconnect_streamed")
            add("1")
            add("-reconnect_delay_max")
            add("5")
            add("-i")
            add(url)
            add("-f")
            add("s16le")
            add("-ar")
            add(sampleRate.toString())
            add("-ac")
            add("1")
            add("-loglevel")
            add("warning")
            add("pipe:1")
        }

        val newProcess = ProcessBuilder(command)
            .redirectErrorStream(false)
            .start()

        process = newProcess
        processId = ProcessLifecycleManager.registerProcess(newProcess)

        launchModCoroutine(Dispatchers.IO) {
            newProcess.waitFor()
            // Clean up when process exits naturally
            if (process == newProcess) {
                processId?.let { ProcessLifecycleManager.destroyProcess(it) }
                process = null
                processId = null
            }
        }
    }

    fun isRunning(): Boolean {
        return process?.isAlive == true
    }

    fun destroy() {
        processId?.let {
            ProcessLifecycleManager.destroyProcess(it)
            processId = null
        }
        process = null
    }
}

