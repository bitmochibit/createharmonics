package me.mochibit.createharmonics.foundation.extension

import net.createmod.catnip.nbt.NBTHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack

inline fun <reified T : Enum<*>> CompoundTag.writeEnum(
    key: String,
    enumValue: T,
) = NBTHelper.writeEnum(this, key, enumValue)

inline fun <reified T : Enum<*>> CompoundTag.readEnum(key: String): T = NBTHelper.readEnum(this, key, T::class.java)

fun ItemStack.toNBT(): CompoundTag {
    val serialized = CompoundTag()
    this.save(serialized)
    return serialized
}
