package me.mochibit.createharmonics.foundation

import com.mojang.logging.LogUtils

/**
 * Logger
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new Logger
 */
object Logger {
    private val logger = LogUtils.getLogger()
    private const val PREFIX = "[CreateHarmonics] "

    /**
     * Info
     *
     * @param message
     */
    fun info(message: String) {
        logger.info(PREFIX + message)
    }

    /**
     * Warn
     *
     * @param message
     */
    fun warn(message: String) {
        logger.warn(PREFIX + message)
    }

    /**
     * Err
     *
     * @param message
     */
    fun err(message: String) {
        logger.error(PREFIX + message)
    }

    /**
     * Debug
     *
     * @param message
     */
    fun debug(message: String) {
        logger.info(PREFIX + "~ DEBUG ~" + message)
    }
}

/**
 * Info on the receiver [String]
 *
 */
fun String.info() = Logger.info(this)

/**
 * Warn on the receiver [String]
 *
 */
fun String.warn() = Logger.warn(this)

/**
 * Err on the receiver [String]
 *
 */
fun String.err() = Logger.err(this)

/**
 * Debug on the receiver [String]
 *
 */
fun String.debug() = Logger.debug(this)
