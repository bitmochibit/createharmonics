package me.mochibit.createharmonics

import com.mojang.logging.LogUtils

object Logger {
    private val logger = LogUtils.getLogger()
    private const val prefix = "[CreateHarmonics] "

    fun info(message: String) {
        logger.info(prefix+message)
    }

    fun warn(message: String) {
        logger.warn(prefix+message)
    }

    fun err(message: String) {
        logger.error(prefix+message)
    }

    fun debug(message: String) {
        logger.debug(prefix+message)
    }
}

