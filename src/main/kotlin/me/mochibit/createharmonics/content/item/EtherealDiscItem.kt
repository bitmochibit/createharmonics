package me.mochibit.createharmonics.content.item

import me.mochibit.createharmonics.CreateHarmonicsMod
import me.mochibit.createharmonics.audio.PitchFunction
import me.mochibit.createharmonics.audio.YoutubePlayer
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.Sound
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.SoundManager
import net.minecraft.client.sounds.WeighedSoundEvents
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import net.minecraft.util.valueproviders.ConstantFloat
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

class EtherealDiscItem : Item(Properties().stacksTo(1)) {

    override fun use(pLevel: Level, pPlayer: Player, pUsedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        if (!pLevel.isClientSide) return InteractionResultHolder.pass(pPlayer.getItemInHand(pUsedHand))
        val itemStack = pPlayer.getItemInHand(pUsedHand)

        val youtubeUrl = "https://www.youtube.com/watch?v=NLj6k85SEBk"
        val sampleRate = 48000

        // Example: Use dynamic pitch function instead of constant pitch
        // Uncomment one of these to try different effects:

        // 1. Constant pitch (original behavior)


        // 2. Gradually increase pitch from 0.5x to 1.5x over the song
        // val pitchFunction = PitchFunction.linear(0.5f, 1.5f, 30.0)

        // 3. Vibrato effect (slight pitch oscillation)
        val pitchFunction = PitchFunction.oscillate(1.0f, 0.5f, 1.0)

        // 4. Custom swoosh effect
        // val pitchFunction = PitchFunction.custom { time ->
        //     1.0f + kotlin.math.sin(time * 2.0).toFloat() * 0.3f
        // }

        // Create a unique resource location based on URL and pitch function
        val resourceLocation = createResourceLocation(youtubeUrl, pitchFunction)

        println("EtherealDiscItem: Starting immediate pipeline for: $youtubeUrl")
        println("EtherealDiscItem: Using resource location: $resourceLocation")

        // Use the immediate pipeline stream with dynamic pitch function
        YoutubePlayer.streamAudioWithPitchShift(
            youtubeUrl,
            pitchFunction,
            sampleRate,
            resourceLocation
        )

        // Create and play the sound immediately
        val soundInstance = YouTubeSoundInstance(
            resourceLocation,
            pPlayer.blockPosition(),
            16
        )

        println("EtherealDiscItem: Playing sound with dynamic pitch function")
        Minecraft.getInstance().soundManager.play(soundInstance)

        return InteractionResultHolder.sidedSuccess(itemStack, pLevel.isClientSide)
    }

    override fun getDescriptionId(): String {
        return "item.createharmonics.ethereal_disc"
    }

    companion object {
        /**
         * Create a unique ResourceLocation based on YouTube URL and pitch function.
         * This allows the same URL with different pitch to be played simultaneously.
         */
        fun createResourceLocation(youtubeUrl: String, pitchFunction: PitchFunction): ResourceLocation {
            val hash = "$youtubeUrl|${pitchFunction.hashCode()}".hashCode().toString(16)
            return ResourceLocation.fromNamespaceAndPath(
                CreateHarmonicsMod.MOD_ID,
                "youtube_$hash"
            )
        }
    }
}

class YouTubeSoundInstance(
    private val resourceLocation: ResourceLocation,
    private val position: BlockPos,
    private val radius: Int,
    private val pitch: Float = 1.0f
) : SoundInstance {

    init {
        println("YouTubeSoundInstance: Created with resource location: $resourceLocation")
    }

    override fun getLocation(): ResourceLocation {
        return resourceLocation
    }

    override fun resolve(soundManager: SoundManager): WeighedSoundEvents? {
        println("YouTubeSoundInstance: resolve() called for $resourceLocation")
        return WeighedSoundEvents(this.location, null)
    }

    override fun getSound(): Sound {
        println("YouTubeSoundInstance: getSound() called for $resourceLocation")
        return Sound(
            this.location.toString(),
            ConstantFloat.of(this.volume),
            ConstantFloat.of(this.pitch),
            1,
            Sound.Type.SOUND_EVENT,
            true,
            false,
            radius
        )
    }

    override fun getSource(): SoundSource {
        return SoundSource.RECORDS
    }

    override fun isLooping(): Boolean {
        return false
    }

    override fun isRelative(): Boolean {
        return false
    }

    override fun getDelay(): Int {
        return 0
    }

    override fun getVolume(): Float {
        return 1.0f
    }

    override fun getPitch(): Float {
        return pitch
    }

    override fun getX(): Double {
        return position.x.toDouble()
    }

    override fun getY(): Double {
        return position.y.toDouble()
    }

    override fun getZ(): Double {
        return position.z.toDouble()
    }

    override fun getAttenuation(): SoundInstance.Attenuation {
        return SoundInstance.Attenuation.LINEAR
    }
}