package me.mochibit.createharmonics.config

import net.createmod.catnip.config.ConfigBase

/**
 * Common config
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new CommonConfig
 */
object CommonConfig : ConfigBase() {
    override fun getName(): String = "common"
}
