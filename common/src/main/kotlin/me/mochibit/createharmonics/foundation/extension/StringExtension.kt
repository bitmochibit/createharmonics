package me.mochibit.createharmonics.foundation.extension

import me.mochibit.createharmonics.CreateHarmonicsMod.MOD_ID
import net.minecraft.resources.ResourceLocation

/**
 * Converts the resource on the receiver [String]
 *
 * @return the resource location
 */
fun String.asResource(): ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, this)

/**
 * Res path on the receiver [String]
 *
 * @param other
 * @return the resource location
 */
infix fun String.resPath(other: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(this, other)
