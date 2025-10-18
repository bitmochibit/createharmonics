package me.mochibit.createharmonics.audio

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import me.mochibit.createharmonics.Logger.err
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.audio.provider.FFMPEG
import me.mochibit.createharmonics.audio.provider.YTDL
import me.mochibit.createharmonics.coroutine.ModCoroutineManager
import net.minecraft.resources.ResourceLocation
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Buffered input stream that pre-buffers audio data before making it available.
 * Uses Kotlin Flow for efficient streaming.
 */
class BufferedYouTubeStream(
    private val url: String,
    private val pitchFunction: PitchFunction,
    private val sampleRate: Int,
    private val resourceLocation: ResourceLocation
) : InputStream() {
    private val channel = Channel<ByteArray>(capacity = Channel.BUFFERED)
    private var currentChunk: ByteArray? = null
    private var currentPosition = 0
    private var streamJob: Job? = null

    @Volatile
    private var finished = false

    @Volatile
    private var error: Exception? = null

    private val preBufferSize = 3

    @Volatile
    private var preBuffered = false

    init {
        startPipeline()
    }

    private fun startPipeline() {
        streamJob = ModCoroutineManager.launch(Dispatchers.IO) {
            try {
                info("Starting buffered pipeline for URL: $url")

                YoutubePlayer.processAudioPipeline(url, pitchFunction, sampleRate)
                    .onEach { chunk ->
                        channel.send(chunk)
                        if (!preBuffered && channel.isEmpty.not()) {
                            preBuffered = true
                            info("Pre-buffering completed")
                        }
                    }
                    .catch { e ->
                        error = e as? Exception ?: Exception(e)
                        err("Pipeline error: ${e.message}")
                    }
                    .onCompletion {
                        finished = true
                        channel.close()
                        // Unregister stream when finished
                        StreamRegistry.unregisterStream(resourceLocation)
                        info("Pipeline finished and stream unregistered")
                    }
                    .collect()
            } catch (e: Exception) {
                error = e
                err("Pipeline error: ${e.message}")
                e.printStackTrace()
                channel.close(e)
                StreamRegistry.unregisterStream(resourceLocation)
            }
        }
    }

    override fun read(): Int {
        val chunk = getCurrentChunk() ?: return -1
        val byte = chunk[currentPosition++].toInt() and 0xFF

        if (currentPosition >= chunk.size) {
            currentChunk = null
            currentPosition = 0
        }

        return byte
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0

        val chunk = getCurrentChunk() ?: return -1

        val available = chunk.size - currentPosition
        val toRead = minOf(len, available)

        System.arraycopy(chunk, currentPosition, b, off, toRead)
        currentPosition += toRead

        if (currentPosition >= chunk.size) {
            currentChunk = null
            currentPosition = 0
        }

        return toRead
    }

    private fun getCurrentChunk(): ByteArray? {
        currentChunk?.let { return it }

        error?.let { throw it }

        // Try to receive a chunk (blocking)
        return runBlocking {
            try {
                channel.receiveCatching().getOrNull()?.also {
                    currentChunk = it
                    currentPosition = 0
                }
            } catch (e: Exception) {
                err("Error receiving chunk: ${e.message}")
                null
            }
        }
    }

    override fun available(): Int {
        return currentChunk?.let { it.size - currentPosition }
            ?: if (!finished) 1 else 0
    }

    override fun close() {
        streamJob?.cancel()
        channel.close()
        StreamRegistry.unregisterStream(resourceLocation)
        super.close()
    }
}

/**
 * Handles streaming and processing of YouTube audio with pitch shifting.
 * Pipeline: yt-dlp URL -> FFmpeg decode to PCM -> AudioEffectProcessor -> FFmpeg encode to OGG -> output
 */
object YoutubePlayer {
    private val providersReady = AtomicBoolean(false)
    private val providersInitializing = AtomicBoolean(false)

    private const val DEFAULT_BUFFER_SIZE = 262144 // 256KB
    private const val DEFAULT_SAMPLE_RATE = 48000
    private const val CHUNK_SIZE = 16384

    /**
     * Initialize audio providers (yt-dlp and FFmpeg).
     */
    fun initializeProviders() {
        if (providersReady.get() || !providersInitializing.compareAndSet(false, true)) {
            return
        }

        try {
            info("Initializing audio providers...")
            val ytdlSuccess = YTDL.isAvailable() || YTDL.install()
            val ffmpegSuccess = FFMPEG.isAvailable() || FFMPEG.install()

            if (ytdlSuccess && ffmpegSuccess) {
                providersReady.set(true)
                info("Audio providers ready")
            } else {
                err("Failed to initialize audio providers")
            }
        } finally {
            providersInitializing.set(false)
        }
    }

    /**
     * Stream audio from YouTube URL with constant pitch shifting using immediate pipeline.
     */
    fun streamAudioWithPitchShift(
        url: String,
        pitchShiftFactor: Float = 1.0f,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        resourceLocation: ResourceLocation
    ): InputStream {
        return streamAudioWithPitchShift(url, PitchFunction.constant(pitchShiftFactor), sampleRate, resourceLocation)
    }

    /**
     * Stream audio from YouTube URL with dynamic pitch function using immediate pipeline.
     * Returns a stream that starts producing data immediately while processing continues.
     * Registers the stream in StreamRegistry for lookup by resource location.
     */
    fun streamAudioWithPitchShift(
        url: String,
        pitchFunction: PitchFunction,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        resourceLocation: ResourceLocation
    ): InputStream {
        ensureProvidersReady()
        info("Creating buffered stream for: $url with dynamic pitch function")
        val stream = BufferedYouTubeStream(url, pitchFunction, sampleRate, resourceLocation)

        // Register the stream immediately
        StreamRegistry.registerStream(resourceLocation, stream)

        return stream
    }

    /**
     * Process audio pipeline and return a Flow of audio chunks.
     * This is the core processing pipeline using Kotlin Flow for better streaming.
     */
    suspend fun processAudioPipeline(
        url: String,
        pitchFunction: PitchFunction,
        sampleRate: Int
    ): Flow<ByteArray> = channelFlow {
        // Step 1: Extract audio URL using cache
        info("Getting audio URL from cache for: $url")
        val audioUrl = AudioUrlCache.getAudioUrl(url)
            ?: throw IllegalStateException("Failed to extract audio URL from: $url")
        info("Got audio URL successfully")

        info("Starting audio pipeline with dynamic pitch function")

        // Step 2: Create PCM decode stream
        val pcmStream = PipedInputStream(DEFAULT_BUFFER_SIZE)
        val pcmOutput = PipedOutputStream(pcmStream)

        coroutineScope {
            // Decode job runs concurrently
            val decodeJob = launch {
                pcmOutput.use { output ->
                    try {
                        info("Starting FFmpeg decode to PCM")
                        ffmpegDecodeToPCM(audioUrl, sampleRate, output)
                        info("FFmpeg decode completed")
                    } catch (e: Exception) {
                        err("Decode error: ${e.message}")
                        throw e
                    }
                }
            }

            try {
                // Step 3 & 4: Process and encode to OGG
                processAndEncodeToFlow(pcmStream, pitchFunction, sampleRate)
                    .collect { chunk -> send(chunk) }
            } finally {
                decodeJob.cancel()
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Decode audio URL to raw PCM using FFmpeg.
     */
    private suspend fun ffmpegDecodeToPCM(
        audioUrl: String,
        sampleRate: Int,
        outputStream: PipedOutputStream
    ) = withContext(Dispatchers.IO) {
        val ffmpegPath = FFMPEG.getExecutablePath()
            ?: throw IllegalStateException("FFmpeg not found")

        val command = listOf(
            ffmpegPath,
            "-i", audioUrl,
            "-f", "s16le",
            "-ar", sampleRate.toString(),
            "-ac", "1",
            "-loglevel", "error",
            "pipe:1"
        )

        val process = ProcessBuilder(command).start()
        try {
            val buffer = ByteArray(CHUNK_SIZE)
            var totalBytes = 0L

            process.inputStream.use { input ->
                while (isActive) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) break

                    outputStream.write(buffer, 0, bytesRead)
                    outputStream.flush()
                    totalBytes += bytesRead
                }
            }

            info("Decoded $totalBytes bytes of PCM")
        } finally {
            process.destroy()
        }
    }

    /**
     * Process PCM through AudioEffectProcessor and encode to OGG, returning a Flow of chunks.
     */
    private suspend fun processAndEncodeToFlow(
        pcmStream: InputStream,
        pitchFunction: PitchFunction,
        sampleRate: Int
    ): Flow<ByteArray> = channelFlow {
        val ffmpegPath = FFMPEG.getExecutablePath()
            ?: throw IllegalStateException("FFmpeg not found")

        val command = listOf(
            ffmpegPath,
            "-f", "s16le",
            "-ar", sampleRate.toString(),
            "-ac", "1",
            "-i", "pipe:0",
            "-f", "ogg",
            "-acodec", "libvorbis",
            "-q:a", "6",
            "-loglevel", "warning",
            "pipe:1"
        )

        info("Starting FFmpeg encoder process")
        val process = ProcessBuilder(command).start()
        try {
            coroutineScope {
                // Stream OGG output
                val streamJob = launch {
                    val buffer = ByteArray(CHUNK_SIZE)
                    var totalBytes = 0L

                    process.inputStream.use { input ->
                        while (isActive) {
                            val bytesRead = input.read(buffer)
                            if (bytesRead == -1) break

                            val chunk = buffer.copyOf(bytesRead)
                            send(chunk)
                            totalBytes += bytesRead
                        }
                    }

                    info("OGG output streaming completed: $totalBytes bytes")
                }

                // Feed processed PCM to FFmpeg (runs after stream job starts)
                val feedJob = launch {
                    process.outputStream.use { ffmpegInput ->
                        try {
                            info("Starting audio processing with dynamic pitch")
                            val effectProcessor = AudioEffectProcessor(sampleRate)
                            effectProcessor.processPCMStream(pcmStream, ffmpegInput, pitchFunction)
                            info("Audio processing completed")
                        } catch (e: Exception) {
                            err("Processing error: ${e.message}")
                            throw e
                        }
                    }
                }

                // Wait for both jobs to complete
                feedJob.join()
                streamJob.join()
            }
        } finally {
            process.destroy()
        }
    }.flowOn(Dispatchers.IO)

    private fun ensureProvidersReady() {
        if (!providersReady.get()) {
            initializeProviders()
            require(providersReady.get()) { "Audio providers not ready" }
        }
    }

    fun shutdown() {
        // Clear caches and streams
        StreamRegistry.clear()
        AudioUrlCache.clear()
        info("YoutubePlayer shutting down")
    }
}
