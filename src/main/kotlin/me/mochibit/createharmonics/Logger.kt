package me.mochibit.createharmonics

import com.mojang.logging.LogUtils

object Logger {
    private val logger = LogUtils.getLogger()

    fun info(message: String) {
        logger.info(message)
    }

    fun warn(message: String) {
        logger.warn(message)
    }

    fun err(message: String) {
        logger.error(message)
    }

    fun debug(message: String) {
        logger.debug(message)
    }
}

