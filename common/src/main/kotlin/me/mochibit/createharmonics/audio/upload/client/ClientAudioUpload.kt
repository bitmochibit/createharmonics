package me.mochibit.createharmonics.audio.upload.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.mochibit.createharmonics.audio.process.FFprobeExecutor
import me.mochibit.createharmonics.audio.process.YTdlpExecutor
import me.mochibit.createharmonics.foundation.async.modLaunch
import me.mochibit.createharmonics.foundation.async.onClientThread
import me.mochibit.createharmonics.foundation.info
import me.mochibit.createharmonics.foundation.network.packet.AudioUploadGrantedPacket
import me.mochibit.createharmonics.foundation.network.packet.RequestAudioUpload
import me.mochibit.createharmonics.foundation.services.networkService
import me.mochibit.createharmonics.foundation.warn
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

data class PreparedUpload(
    val file: Path,
    val title: String?,
    val artist: String?,
    val durationSeconds: Int,
    val sampleRate: Int,
    val cleanupDir: Path?,
) {
    fun cleanup() { cleanupDir?.toFile()?.deleteRecursively() }
}

object UploadPreparer {
    suspend fun fromFile(file: Path): PreparedUpload {
        val p = FFprobeExecutor.probeRaw(file.toAbsolutePath().toString())
        return PreparedUpload(file, p.title, p.artist, p.durationSeconds, p.sampleRate.roundToInt(), null)
    }

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

object ClientAudioUpload {
    private class Pending(val prepared: PreparedUpload, val pos: BlockPos)

    private val http = HttpClient.newHttpClient()
    @Volatile private var pending: Pending? = null

    @Volatile var isBusy = false
        private set

    fun beginFile(file: Path, pos: BlockPos) = begin(pos) { UploadPreparer.fromFile(file) }
    fun beginUrl(url: String, pos: BlockPos) = begin(pos) { UploadPreparer.fromUrl(url) }

    private fun begin(pos: BlockPos, prepare: suspend () -> PreparedUpload) {
        if (isBusy) return
        isBusy = true
        modLaunch(Dispatchers.IO) {
            val prepared = try {
                prepare()
            } catch (e: CancellationException) {
                isBusy = false; throw e
            } catch (e: Exception) {
                isBusy = false
                "Can't prepare audio: ${e.message}".warn()
                return@modLaunch
            }
            pending = Pending(prepared, pos)
            onClientThread {
                networkService.sendToServer(RequestAudioUpload(pos, prepared.file.fileName.toString()))
            }
        }
    }

    fun onDenied(reason: String) {
        pending?.prepared?.cleanup()
        pending = null
        isBusy = false
        reason.warn()
    }

    fun onGranted(grant: AudioUploadGrantedPacket) {
        val p = pending?.also { pending = null } ?: return
        val up = p.prepared

        val request = try {
            HttpRequest.newBuilder(
                AudioServerEndpoint.uploadUri(
                    filename = up.file.fileName.toString(),
                    durationSeconds = up.durationSeconds,
                    sampleRate = up.sampleRate,
                    title = up.title,
                    artist = up.artist,
                )
            )
                .header("Authorization", "Bearer ${grant.token}")
                .timeout(Duration.ofMinutes(5))
                .POST(HttpRequest.BodyPublishers.ofFile(up.file))
                .build()
        } catch (e: IOException) {
            finish(up)
            "Can't read the file ${e.message}".warn()
            return
        }

        http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .whenComplete { resp, err ->
                finish(up)
                when {
                    err != null -> "Upload failed: ${err.message}".warn()
                    resp.statusCode() != 201 -> "Upload failed (${resp.statusCode()}): ${resp.body()}".warn()
                    else -> "Upload completed".info()
                }
            }
    }

    private fun finish(up: PreparedUpload) {
        up.cleanup()
        isBusy = false
    }
}