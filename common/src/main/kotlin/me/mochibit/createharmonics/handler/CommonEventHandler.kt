package me.mochibit.createharmonics.handler

import me.mochibit.createharmonics.foundation.eventbus.ModEventHandler

/**
 * Common event handler
 * Sealed hierarchy — all subtypes are known at compile time, ideal for exhaustive `when`.
 *
 */
sealed interface CommonEventHandler : ModEventHandler
