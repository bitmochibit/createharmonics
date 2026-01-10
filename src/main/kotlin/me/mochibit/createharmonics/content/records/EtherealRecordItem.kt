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
         * Handles record usage by applying durability damage.
         * @param stack The ethereal record item stack
         * @param random Random source for durability calculations
         * @return RecordUseResult containing the result of the usage
         */
        fun handleRecordUse(
            stack: ItemStack,
            random: net.minecraft.util.RandomSource,
        ): RecordUseResult {
            if (stack.item !is EtherealRecordItem) {
                return RecordUseResult.Invalid
            }

            val etherealRecord = stack.item as EtherealRecordItem

            // If not damageable (infinite uses), return the same stack
            if (!etherealRecord.isDamageable(stack)) {
                return RecordUseResult.NotDamageable(stack)
            }

            // Apply damage
            val damaged = stack.copy()
            val broken = damaged.hurt(1, random, null)

            return if (broken) {
                // Get the crafted-from record
                val craftedWithDisc = RecordCraftingHandler.getCraftedWithDisc(stack)

                // Create a BaseRecordItem with the craftedWith data preserved
                val baseRecordStack =
                    ItemStack(
                        me.mochibit.createharmonics.registry.ModItems.BASE_RECORD
                            .get(),
                    )
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
