package me.mochibit.createharmonics.content.records

import me.mochibit.createharmonics.audio.bin.FFMPEGProvider
import me.mochibit.createharmonics.audio.bin.YTDLProvider
import me.mochibit.createharmonics.audio.effect.AudioEffect
import me.mochibit.createharmonics.audio.effect.PitchShiftEffect
import me.mochibit.createharmonics.audio.player.AudioPlayer
import me.mochibit.createharmonics.audio.player.AudioRequest
import me.mochibit.createharmonics.audio.source.StreamAudioSource
import me.mochibit.createharmonics.audio.stream.Ogg2PcmInputStream
import me.mochibit.createharmonics.audio.utils.getStreamDirectly
import me.mochibit.createharmonics.foundation.registry.ModItems
import me.mochibit.createharmonics.foundation.services.contentService
import me.mochibit.createharmonics.foundation.supplier.values.FloatSupplier
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.RecordItem
import javax.sound.sampled.AudioSystem

// TODO This will be refactored to be more robust
object RecordUtilities {
    const val AUDIO_URL_TAG_KEY = "audio_url"

    fun getAudioUrl(stack: ItemStack): String? {
        if (!contentService.isEtherealRecord(stack)) return null
        return stack.tag?.getString(AUDIO_URL_TAG_KEY)
    }

    fun setAudioUrl(
        stack: ItemStack,
        url: String,
    ) {
        if (!contentService.isEtherealRecord(stack)) return

        if (stack.tag == null) {
            stack.tag = CompoundTag()
        }
        stack.tag?.putString(AUDIO_URL_TAG_KEY, url)
    }

    /**
     * Handles record usage by applying durability damage.
     * @param stack The ethereal record item stack
     * @param random Random source for durability calculations
     * @return RecordUseResult containing the result of the usage
     */
    fun handleRecordUse(
        stack: ItemStack,
        random: RandomSource,
    ): RecordUseResult {
        if (!contentService.isEtherealRecord(stack)) return RecordUseResult.Invalid

        if (!contentService.isEtherealRecordDamageable(stack)) {
            return RecordUseResult.NotDamageable(stack)
        }

        // Apply damage
        val damaged = stack.copy()
        val broken = damaged.hurt(1, random, null)

        return if (broken) {
            // Get the crafted-from record
            val craftedWithDisc = RecordCraftingHandler.getCraftedWithDisc(stack)

            // Create a BaseRecordItem with the craftedWith data preserved
            val baseRecordStack = ItemStack(ModItems.BASE_RECORD.get())
            if (!craftedWithDisc.isEmpty) {
                RecordCraftingHandler.setCraftedWithDisc(baseRecordStack, craftedWithDisc)
            }

            RecordUseResult.Broken(baseRecordStack)
        } else {
            RecordUseResult.Damaged(damaged)
        }
    }

    /**
     * Result of handling record use
     */
    sealed class RecordUseResult {
        /** The record is not damageable (infinite uses) */
        data class NotDamageable(
            val stack: ItemStack,
        ) : RecordUseResult()

        /** The record was damaged but not broken */
        data class Damaged(
            val stack: ItemStack,
        ) : RecordUseResult()

        /** The record broke and should drop the base record */
        data class Broken(
            val dropStack: ItemStack,
        ) : RecordUseResult()

        /** Invalid input (not an ethereal record) */
        data object Invalid : RecordUseResult()

        val isBroken: Boolean get() = this is Broken
        val shouldReplace: Boolean get() = this is Damaged || this is NotDamageable
        val replacementStack: ItemStack?
            get() =
                when (this) {
                    is Damaged -> stack
                    is NotDamageable -> stack
                    else -> null
                }
    }

    /**
     * Play the audio directly from a record item stack
     * @param etherealRecord The record used for gathering information about the audio to play
     * @param offsetSeconds Playback offset, used mainly for synchronization
     * @param compPitchSupplier Pitch shift function supplier for composition effects
     */
    fun AudioPlayer.playFromRecord(
        etherealRecord: ItemStack,
        compPitchSupplier: FloatSupplier = FloatSupplier { 1f },
        compRadiusSupplier: FloatSupplier = FloatSupplier { 1f },
        compVolumeSupplier: FloatSupplier = FloatSupplier { 1f },
    ) {
        if (!contentService.isEtherealRecord(etherealRecord)) return
        val url = getAudioUrl(etherealRecord) ?: ""

        // source -> |INTRINSIC_EFFECT| -> |EFFECT_COMP_MIXER|(untouched by intrinsics) -> |PITCH SHIFT| -> |WATER MUFFLE|

        val recordProps = contentService.getEtherealRecordType(etherealRecord) ?: return
        this.soundEventComposition.removeAll { true }

        this.soundEventComposition.anchorAfter(AudioEffect.Scope.INTRINSIC_EFFECT)

        val soundEvents = recordProps.properties.soundEventCompProvider()
        for (event in soundEvents) {
            event.pitchSupplier = compPitchSupplier
            event.radiusSupplier = compRadiusSupplier
            event.volumeSupplier = compVolumeSupplier
            this.soundEventComposition.add(event)
        }

        this.effectChain.cleanAllExceptScopes(AudioEffect.Scope.MACHINE_CONTROLLED_PITCH)

        recordProps.properties.audioEffectsProvider().forEach { effect ->
            this.effectChain.addBeforeScope(AudioEffect.Scope.MACHINE_CONTROLLED_PITCH, effect)
        }

        if (url.isNotBlank() && FFMPEGProvider.isAvailable() && YTDLProvider.isAvailable()) {
            this.request(
                AudioRequest.Url(url),
            )
            return this.play()
        }

        // Try to play audio from the crafted-from record
        val craftedWith = RecordCraftingHandler.getCraftedWithDisc(etherealRecord).item as? RecordItem ?: return
        val sampleRate =
            craftedWith.sound.getStreamDirectly(false).get().use { audio ->
                audio.format.sampleRate
            }

        this.request(
            AudioRequest.Stream(
                {
                    Ogg2PcmInputStream(craftedWith.sound.getStreamDirectly(false).get())
                },
                StreamAudioSource.Information(
                    name = craftedWith.displayName.getString(48),
                    bitrate = sampleRate.toInt(),
                    duration = 1000,
                ),
            ),
        )
        this.play()
    }
}
