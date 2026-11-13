package me.mochibit.createharmonics.foundation.extension

import net.createmod.catnip.nbt.NBTHelper
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.world.item.ItemStack

/**
 * Sets the enum on the receiver [CompoundTag]
 *
 * @param T
 * @param key
 * @param enumValue the enum value
 */
inline fun <reified T : Enum<*>> CompoundTag.writeEnum(
    key: String,
    enumValue: T,
) = NBTHelper.writeEnum(this, key, enumValue)

/**
 * Returns the enum on the receiver [CompoundTag]
 *
 * @param T
 * @param key
 * @return the t
 */
inline fun <reified T : Enum<*>> CompoundTag.readEnum(key: String): T = NBTHelper.readEnum(this, key, T::class.java)

/**
 * Converts the nbt on the receiver [ItemStack]
 *
 * @param registries
 * @return the tag
 */
fun ItemStack.toNBT(registries: HolderLookup.Provider): Tag = this.saveOptional(registries)
