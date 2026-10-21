package me.mochibit.createharmonics.audio.upload.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import me.mochibit.createharmonics.audio.upload.ServerAudioReference
import me.mochibit.createharmonics.foundation.async.modLaunch
import net.minecraft.client.Minecraft
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

object ServerAudioCache {
    private val root: Path by lazy {
        Minecraft.getInstance().gameDirectory.toPath()
            .toAbsolutePath()
            .normalize()
            .resolve("createharmonics/audio-cache")
    }
    private val inflight = ConcurrentHashMap.newKeySet<String>()

    private fun path(ref: ServerAudioReference, ext: String): Path {
        val p = root.resolve(ref.owner).resolve("${ref.fileId}.$ext").normalize()
        require(p.startsWith(root)) { "Invalid cache path" }
        return p
    }

    fun cachedFile(ref: ServerAudioReference, ext: String): Path? =
        path(ref, ext).takeIf { Files.isRegularFile(it) }?.also {
            runCatching { Files.setLastModifiedTime(it, FileTime.from(Instant.now())) }
        }

    fun prefetch(ref: ServerAudioReference, ext: String) {
        val target = path(ref, ext)
        val key = "${ref.owner}/${ref.fileId}"
        if (Files.exists(target) || !inflight.add(key)) return

        modLaunch(Dispatchers.IO) {
            val tmp = target.resolveSibling("${target.fileName}.part")
            try {
                Files.createDirectories(target.parent)
                AudioServerClient.download(AudioServerEndpoint.streamUrl(ref, ext), tmp)
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: Exception) {
                Files.deleteIfExists(tmp)
            } finally {
                inflight.remove(key)
            }
        }
    }

    fun evict(ref: ServerAudioReference) {
        val dir = root.resolve(ref.owner).normalize().takeIf { it.startsWith(root) && Files.isDirectory(it) } ?: return
        Files.newDirectoryStream(dir, "${ref.fileId}.*").use { files ->
            files.forEach { runCatching { Files.deleteIfExists(it) } }
        }
    }
}