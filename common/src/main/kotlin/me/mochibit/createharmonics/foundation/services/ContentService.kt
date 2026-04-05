package me.mochibit.createharmonics.foundation.services

import me.mochibit.createharmonics.audio.instance.StreamingSoundInstance
import me.mochibit.createharmonics.content.records.RecordType
import me.mochibit.createharmonics.foundation.registry.platform.ModSoundRegistry
import me.mochibit.createharmonics.foundation.supplier.values.FloatSupplier
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.material.FluidState
import java.io.InputStream

interface ContentService {
    /**
     * This one is required since both Forge and Neoforge changed how sound engine retrieves streams
     * allowing custom streams to be injected.
     *
     * For possible Fabric ports this wouldn't work, however in [me.mochibit.createharmonics.audio.player.AudioPlayer]
     * the platform specific play must be handled using mixins to [net.minecraft.client.sounds.SoundEngine].
     */
    fun streamingSoundInstanceFactory(
        stream: InputStream,
        streamId: String,
        soundEvent: SoundEvent,
        sampleRate: Int = 44100,
        soundSource: SoundSource = SoundSource.RECORDS,
        randomSource: RandomSource = RandomSource.create(),
        volumeSupplier: FloatSupplier = FloatSupplier { 1.0f },
        pitchSupplier: FloatSupplier = FloatSupplier { 1.0f },
        posSupplier: () -> BlockPos = { BlockPos.ZERO },
        radiusSupplier: FloatSupplier = FloatSupplier { 64f },
    ): StreamingSoundInstance

    // Helpers for platform-specific features
    fun getViscosity(fluidState: FluidState): Int

    // Ponder stuff
    fun addTags(rawHelper: PonderTagRegistrationHelper<ResourceLocation>)

    fun addScenes(rawHelper: PonderSceneRegistrationHelper<ResourceLocation>)

    // Mod content stuff
    fun contraptionEntityDataChanged(
        entityID: Int,
        localPos: BlockPos,
        newData: CompoundTag,
    )

    fun configureRecordPressBase(
        sender: ServerPlayer,
        blockPos: BlockPos,
        audioUrls: MutableList<String>,
        urlWeights: MutableList<Float>,
        randomMode: Boolean = false,
        newIndex: Int = 0,
    )
}

val contentService: ContentService by lazy {
    loadService<ContentService>()
}
