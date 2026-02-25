package me.mochibit.createharmonics.foundation.extension

import me.mochibit.createharmonics.CreateHarmonicsMod.MOD_ID
import net.minecraft.resources.ResourceLocation

fun String.asResource(): ResourceLocation = ResourceLocation(MOD_ID, this)

infix fun String.resPath(other: String): ResourceLocation = ResourceLocation(this, other)
