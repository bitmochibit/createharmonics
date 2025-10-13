package me.mochibit.createharmonics.extension

import kotlin.time.Duration

fun Duration.inTicks(): Int = (this.inWholeMilliseconds / 50).toInt()