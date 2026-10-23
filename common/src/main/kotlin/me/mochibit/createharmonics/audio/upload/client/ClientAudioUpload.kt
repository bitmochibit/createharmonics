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
    private class Pending(val prepare: suspend () -> PreparedUpload, val pos: BlockPos)

    private val http = HttpClient.newHttpClient()
    @Volatile private var pending: Pending? = null

    @Volatile var isBusy = false
        private set

    fun beginFile(file: Path, pos: BlockPos) =
        begin(pos, file.fileName.toString()) { UploadPreparer.fromFile(file) }

    fun beginUrl(url: String, pos: BlockPos) =
        begin(pos, url) { UploadPreparer.fromUrl(url) }


    private fun begin(pos: BlockPos, label: String, prepare: suspend () -> PreparedUpload) {
        if (isBusy) return
        isBusy = true
        pending = Pending(prepare, pos)
        networkService.sendToServer(RequestAudioUpload(pos, label))
    }

    fun onDenied(reason: AudioUploadResponse) {
        pending = null
        isBusy = false
        reason.sendToChat()
    }

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
        if (resp.statusCode() != 201) throw IOException("(${resp.statusCode()}) ${resp.body()}")
    }

    private suspend fun abort(pos: BlockPos) = onClientThread {
        networkService.sendToServer(CancelAudioUploadPacket(pos))
    }
}

fun AudioUploadResponse.sendToChat() {
    val localPlayer = Minecraft.getInstance().player ?: return
    localPlayer.displayClientMessage(
        ModLang.translate(this.translationKey).component(),
        false
    )
}