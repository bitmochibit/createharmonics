package me.mochibit.createharmonics.audio.upload.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.mochibit.createharmonics.audio.process.FFprobeExecutor
import me.mochibit.createharmonics.audio.process.YTdlpExecutor
import me.mochibit.createharmonics.audio.upload.AudioUploadResponse
import me.mochibit.createharmonics.foundation.async.modLaunch
import me.mochibit.createharmonics.foundation.async.onClientThread
import me.mochibit.createharmonics.foundation.info
import me.mochibit.createharmonics.foundation.locale.ModLang
import me.mochibit.createharmonics.foundation.network.packet.AudioUploadGrantedPacket
import me.mochibit.createharmonics.foundation.network.packet.CancelAudioUploadPacket
import me.mochibit.createharmonics.foundation.network.packet.RequestAudioUpload
import me.mochibit.createharmonics.foundation.services.networkService
import me.mochibit.createharmonics.foundation.warn
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

/**
 * Prepared upload
 * Data class — a value holder; `equals`, `hashCode`, `toString` and `copy` are generated.
 *
 * @constructor Creates a new PreparedUpload
 * @property file
 * @property title
 * @property artist
 * @property durationSeconds the duration seconds
 * @property sampleRate the sample rate
 * @property cleanupDir the cleanup dir
 */
data class PreparedUpload(
    val file: Path,
    val title: String?,
    val artist: String?,
    val durationSeconds: Int,
    val sampleRate: Int,
    val cleanupDir: Path?,
) {
    /**
     * Cleanup
     *
     */
    fun cleanup() { cleanupDir?.toFile()?.deleteRecursively() }
}

/**
 * Upload preparer
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new UploadPreparer
 */
object UploadPreparer {
    /**
     * Returns the file
     * Suspending function — must be called from a coroutine or another suspending function.
     *
     * @param file
     * @return the prepared upload
     */
    suspend fun fromFile(file: Path): PreparedUpload {
        val p = FFprobeExecutor.probeRaw(file.toAbsolutePath().toString())
        return PreparedUpload(file, p.title, p.artist, p.durationSeconds, p.sampleRate.roundToInt(), null)
    }

    /**
     * Returns the url
     * Suspending function — must be called from a coroutine or another suspending function.
     *
     * @param url
     * @return the prepared upload
     * @throws e
     */
    suspend fun fromUrl(url: String): PreparedUpload {
        val dir = withContext(Dispatchers.IO) { Files.createTempDirectory("createharmonics-upload") }
        try {
            val dl = YTdlpExecutor.download(url, dir)
            val p = FFprobeExecutor.probeRaw(dl.file.toString())
            return PreparedUpload(
                dl.file, dl.title ?: p.title, dl.artist ?: p.artist,
                p.durationSeconds, p.sampleRate.roundToInt(), dir,
            )
        } catch (e: Throwable) {
            dir.toFile().deleteRecursively()
            throw e
        }
    }
}

/**
 * Client audio upload
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new ClientAudioUpload
 */
object ClientAudioUpload {
    private class Pending(val prepare: suspend () -> PreparedUpload, val pos: BlockPos)

    private val http = HttpClient.newHttpClient()
    @Volatile private var pending: Pending? = null

    @Volatile var isBusy = false
        private set

    /**
     * Starts the file
     *
     * @param file
     * @param pos
     */
    fun beginFile(file: Path, pos: BlockPos) =
        begin(pos, file.fileName.toString()) { UploadPreparer.fromFile(file) }

    /**
     * Starts the url
     *
     * @param url
     * @param pos
     */
    fun beginUrl(url: String, pos: BlockPos) =
        begin(pos, url) { UploadPreparer.fromUrl(url) }


    private fun begin(pos: BlockPos, label: String, prepare: suspend () -> PreparedUpload) {
        if (isBusy) return
        if (AudioServerEndpoint.port <= 0) {
            AudioUploadResponse.SERVER_NOT_RUNNING.sendToChat()
            return
        }
        isBusy = true
        pending = Pending(prepare, pos)
        networkService.sendToServer(RequestAudioUpload(pos, label))
    }

    /**
     * On denied
     *
     * @param reason
     */
    fun onDenied(reason: AudioUploadResponse) {
        pending = null
        isBusy = false
        reason.sendToChat()
    }

    /**
     * On granted
     *
     * @param grant
     * @throws e
     */
    fun onGranted(grant: AudioUploadGrantedPacket) {
        val p = pending?.also { pending = null } ?: return

        modLaunch(Dispatchers.IO) {
            var prepared: PreparedUpload? = null
            try {
                prepared = p.prepare()
                upload(prepared, grant.token)
                "Upload completed".info()
            } catch (e: CancellationException) {
                abort(p.pos)
                throw e
            } catch (e: Exception) {
                "Upload failed: ${e.message}".warn()
                abort(p.pos)
                val response = (e as? AudioUploadException)?.response ?: AudioUploadResponse.INTERNAL_ERROR
                onClientThread { response.sendToChat() }
            } finally {
                prepared?.cleanup()
                isBusy = false
            }
        }
    }

    private fun upload(up: PreparedUpload, token: String) {
        val request = HttpRequest.newBuilder(
            AudioServerEndpoint.uploadUri(
                filename = up.file.fileName.toString(),
                durationSeconds = up.durationSeconds,
                sampleRate = up.sampleRate,
                title = up.title,
                artist = up.artist,
            )
        )
            .header("Authorization", "Bearer $token")
            .timeout(Duration.ofMinutes(5))
            .POST(HttpRequest.BodyPublishers.ofFile(up.file))
            .build()

        val resp = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() != 201) {
            val responseEnum = runCatching { AudioUploadResponse.valueOf(resp.body().trim()) }
                .getOrDefault(AudioUploadResponse.INTERNAL_ERROR)
            throw AudioUploadException(responseEnum)
        }
    }

    private suspend fun abort(pos: BlockPos) = onClientThread {
        networkService.sendToServer(CancelAudioUploadPacket(pos))
    }
}

/**
 * Sends the to chat on the receiver [AudioUploadResponse]
 *
 */
fun AudioUploadResponse.sendToChat() {
    val localPlayer = Minecraft.getInstance().player ?: return
    localPlayer.displayClientMessage(
        ModLang.translate(this.translationKey).component(),
        false
    )
}

/**
 * Audio upload exception
 *
 * @constructor Creates a new AudioUploadException
 * @property response
 */
class AudioUploadException(val response: AudioUploadResponse) : IOException(response.name)