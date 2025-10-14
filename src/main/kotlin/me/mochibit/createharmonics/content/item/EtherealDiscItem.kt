package me.mochibit.createharmonics.content.item


import me.mochibit.createharmonics.CreateHarmonicsMod
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.AbstractSoundInstance
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
import net.minecraft.world.phys.Vec3
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


class EtherealDiscItem : Item(Properties().stacksTo(1)) {


    override fun use(pLevel: Level, pPlayer: Player, pUsedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        val itemStack = pPlayer.getItemInHand(pUsedHand)

        val test = YouTubeSoundInstance(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            pPlayer.blockPosition(),
            16,
        )

        Minecraft.getInstance().soundManager.play(test)

        return InteractionResultHolder.sidedSuccess(itemStack, pLevel.isClientSide)
    }


    override fun getDescriptionId(): String {
        return "item.createharmonics.ethereal_disc"
    }
}

class YouTubeSoundInstance(
    private val fileName: String,
    private val position: BlockPos,
    private val radius: Int,
    private val pitch: Float = 1.0f
) : SoundInstance {
    override fun getLocation(): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CreateHarmonicsMod.MOD_ID, "ethereal_sound")
    }

    override fun resolve(soundManager: SoundManager): WeighedSoundEvents? {
        return WeighedSoundEvents(this.location, null)
    }

    override fun getSound(): Sound {
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
        return position.x
            .toDouble()
    }

    override fun getY(): Double {
        return position.y
            .toDouble()
    }

    override fun getZ(): Double {
        return position.z
            .toDouble()
    }

    override fun getAttenuation(): SoundInstance.Attenuation {
        return SoundInstance.Attenuation.LINEAR
    }
}