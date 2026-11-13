package me.mochibit.createharmonics.content.records

import me.mochibit.createharmonics.audio.bin.FFMPEGProvider
import me.mochibit.createharmonics.audio.bin.YTDLProvider
import me.mochibit.createharmonics.audio.effect.AudioEffect
import me.mochibit.createharmonics.audio.info.AudioInfo
import me.mochibit.createharmonics.audio.player.AudioPlayer
import me.mochibit.createharmonics.audio.player.AudioRequest
import me.mochibit.createharmonics.audio.stream.Ogg2PcmInputStream
import me.mochibit.createharmonics.audio.utils.getStreamDirectly
import me.mochibit.createharmonics.config.ClientConfig
import me.mochibit.createharmonics.foundation.debug
import me.mochibit.createharmonics.foundation.warn
import me.mochibit.createharmonics.handler.RecordCraftingHandler
import net.minecraft.core.RegistryAccess
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.JukeboxSong
import net.minecraft.world.level.Level
import java.util.concurrent.ConcurrentHashMap

/**
 * Record playback
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new RecordPlayback
 */
object RecordPlayback {
    private var ffprobeWarned = false
    private val sampleRates = ConcurrentHashMap<ResourceLocation, Float>()

    /**
     * Play from record on the receiver [AudioPlayer]
     *
     * @param etherealRecord the ethereal record
     * @param initialPos the initial pos
     * @param level
     */
    fun AudioPlayer.playFromRecord(
        etherealRecord: ItemStack,
        initialPos: Double = 0.0,
        level: Level,
    ) {
        val etherealRecordItem = etherealRecord.item
        if (etherealRecordItem !is EtherealRecordItem) return
        val url = RecordUtilities.getAudioUrl(etherealRecord) ?: ""

        // source -> |INTRINSIC_EFFECT| -> |EFFECT_COMP_MIXER|(untouched by intrinsics) -> |PITCH SHIFT| -> |WATER MUFFLE|

        val recordType = etherealRecordItem.recordType
        val props = recordType.properties(level.registryAccess())
        this.soundEventComposition.clear()
        this.soundEventComposition.anchorBefore(AudioEffect.Scope.MACHINE_CONTROLLED_PITCH)

        for (data in props.soundEvents) {
            val event = data.create()
            event.pitchSupplier = this.masterPitchInterpolator
            event.radiusSupplier = this.masterRadiusInterpolator
            event.volumeSupplier = this.masterVolumeInterpolator
            this.soundEventComposition.add(event)
        }

        this.effectChain.cleanAllExceptScopes(AudioEffect.Scope.MACHINE_CONTROLLED_PITCH)
        props.audioEffects.forEach { def ->
            this.effectChain.addBeforeScope(AudioEffect.Scope.MACHINE_CONTROLLED_PITCH, def.create())
        }

        val craftedWithItem = RecordCraftingHandler.getCraftedWithDisc(etherealRecord).copy()
        val access = level.registryAccess()

        if (url.isNotBlank() && FFMPEGProvider.isAvailable() && YTDLProvider.isAvailable()) {
            if (FFMPEGProvider.isProbeAvailable()) {
                this.fallbackProvider = { discRequest(craftedWithItem, access) }
                this.request(AudioRequest.Url(url))
                this.play(initialPos)
                return
            }
            else if (!ffprobeWarned)
            {
                ffprobeWarned = true
                "FFmpeg is correctly installed but it seems ffprobe is missing! \nIf it was installed by the mod please report this here https://github.com/bitmochibit/createharmonics/issues.\n If it was installed manually, make sure ffprobe executable is in the same folder as ffmpeg".warn()
            }
        }

        if (ClientConfig.debugAudioPlayer.get()) {
            (
                    "Url play request was ignored! Is it intended?\n" +
                            "current url = $url\n" +
                            "ffmpeg available = ${FFMPEGProvider.isAvailable()}\n" +
                            "ytdlp available = ${YTDLProvider.isAvailable()}\n" +
                            "ffprobe available = ${FFMPEGProvider.isProbeAvailable()}\n\n" +
                            "Generally if this is intended, you shouldn't see any AudioPlayer fail notices!"
                    ).debug()
        }

        this.fallbackProvider = null
        discRequest(craftedWithItem, access)?.let {
            this.request(it)
            this.play(initialPos)
        }
    }


    private fun discRequest(disc: ItemStack, access: RegistryAccess): AudioRequest.Stream? {
        val song = JukeboxSong.fromStack(access, disc)
        if (!song.isPresent) return null

        val songData = song.get().value()
        val soundEvent = songData.soundEvent.value()
        val sampleRate = sampleRates.getOrPut(soundEvent.location) {
            soundEvent.getStreamDirectly(false).get().use { it.format.sampleRate }
        }

        return AudioRequest.Stream(
            { Ogg2PcmInputStream(soundEvent.getStreamDirectly(false).get()) },
            AudioInfo(
                audioUrl = "stream",
                durationSeconds = 1000,
                displayName = songData.description.getString(128),
                sampleRate = sampleRate,
                isLive = false,
            ),
        )
    }
}