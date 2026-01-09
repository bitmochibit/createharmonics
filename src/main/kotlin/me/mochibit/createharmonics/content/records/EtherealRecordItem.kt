package me.mochibit.createharmonics.content.records

import me.mochibit.createharmonics.audio.AudioPlayer
import me.mochibit.createharmonics.audio.binProvider.FFMPEGProvider
import me.mochibit.createharmonics.audio.binProvider.YTDLProvider
import me.mochibit.createharmonics.audio.comp.SoundEventComposition
import me.mochibit.createharmonics.audio.effect.EffectChain
import me.mochibit.createharmonics.audio.effect.getStreamDirectly
import me.mochibit.createharmonics.audio.stream.Ogg2PcmInputStream
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.RecordItem
import net.minecraftforge.registries.ForgeRegistries

class EtherealRecordItem(
    val recordType: RecordType,
    props: Properties,
) : Item(props) {
    companion object {
        const val AUDIO_URL_TAG_KEY = "audio_url"

        val musicDiscs =
            listOf(
                Items.MUSIC_DISC_13,
                Items.MUSIC_DISC_CAT,
                Items.MUSIC_DISC_BLOCKS,
                Items.MUSIC_DISC_CHIRP,
                Items.MUSIC_DISC_FAR,
                Items.MUSIC_DISC_MALL,
                Items.MUSIC_DISC_MELLOHI,
                Items.MUSIC_DISC_STAL,
                Items.MUSIC_DISC_STRAD,
                Items.MUSIC_DISC_WARD,
                Items.MUSIC_DISC_11,
                Items.MUSIC_DISC_WAIT,
                Items.MUSIC_DISC_PIGSTEP,
                Items.MUSIC_DISC_OTHERSIDE,
                Items.MUSIC_DISC_5,
                Items.MUSIC_DISC_RELIC,
            )

        fun getAudioUrl(stack: ItemStack): String? {
            if (stack.item !is EtherealRecordItem) return null
            return stack.tag?.getString(AUDIO_URL_TAG_KEY)
        }

        fun setAudioUrl(
            stack: ItemStack,
            url: String,
        ) {
            if (stack.item !is EtherealRecordItem) return

            if (stack.tag == null) {
                stack.tag = CompoundTag()
            }
            stack.tag?.putString(AUDIO_URL_TAG_KEY, url)
        }

        /**
         * Play the audio directly from a record item stack
         * @param etherealRecord The record used for gathering information about the audio to play
         * @param offsetSeconds Playback offset, used mainly for synchronization
         * @param compPitchSupplier Pitch shift function supplier for composition effects
         */
        fun AudioPlayer.playFromRecord(
            etherealRecord: ItemStack,
            offsetSeconds: Double,
            compPitchSupplier: () -> Float = { 1f },
        ) {
            val url = getAudioUrl(etherealRecord) ?: ""

            val recordProps = (etherealRecord.item as EtherealRecordItem).recordType.properties
            val soundEvents = recordProps.soundEventCompProvider()
            for (event in soundEvents) {
                event.pitchSupplier = compPitchSupplier
            }

            if (url.isNotBlank() && FFMPEGProvider.isAvailable() && YTDLProvider.isAvailable()) {
                return this.play(
                    url,
                    EffectChain(
                        recordProps.audioEffectsProvider(),
                    ),
                    SoundEventComposition(soundEvents),
                    offsetSeconds,
                )
            }

            // Try to play audio from the crafted-from record
            val craftedWith = RecordCraftingHandler.getCraftedWithDisc(etherealRecord).item as? RecordItem ?: return
            val stream = craftedWith.sound.getStreamDirectly(false).get()

            this.playFromStream(
                Ogg2PcmInputStream(stream),
                craftedWith.displayName.getString(48),
                EffectChain(
                    recordProps.audioEffectsProvider(),
                ),
                SoundEventComposition(soundEvents),
                offsetSeconds,
            )
        }
    }

    override fun getMaxDamage(stack: ItemStack): Int {
        val uses = recordType.uses
        return if (uses > 0) uses + 1 else 0
    }

    override fun isDamageable(stack: ItemStack): Boolean = recordType.uses > 0

    override fun getDefaultInstance(): ItemStack {
        val default = super.getDefaultInstance()
        RecordCraftingHandler.setCraftedWithDisc(default, ItemStack(musicDiscs.random()))
        return default
    }

    override fun getDescriptionId(): String =
        "item.${ForgeRegistries.ITEMS.getKey(this)?.namespace}.${ForgeRegistries.ITEMS.getKey(this)?.path}"
}
