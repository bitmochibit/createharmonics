package me.mochibit.createharmonics.foundation.services

import me.mochibit.createharmonics.content.records.RecordType
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.material.FluidState

interface ContentService {
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

    val slidingStoneSound: SoundEvent
    val glitterSoundEvent: SoundEvent
    val baseRecordItemStack: ItemStack

    fun onStreamEnd(
        audioPlayerId: String,
        failure: Boolean,
    ): Boolean

    fun onTitleChange(
        audioPlayerId: String,
        audioName: String,
    ): Boolean

    fun isEtherealRecord(stack: ItemStack): Boolean

    fun getEtherealRecordType(stack: ItemStack): RecordType?

    fun isEtherealRecordDamageable(stack: ItemStack): Boolean

    fun isRecordBase(stack: ItemStack): Boolean
}

val contentService: ContentService by lazy {
    loadService<ContentService>()
}
