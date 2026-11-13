package me.mochibit.createharmonics.foundation.services

import java.nio.file.Path

/**
 * Platform service
 *
 * @property currentPlatform the current platform
 * @property environment
 * @property currentThreadSide the current thread side
 * @property serverRootPath the server root path
 */
interface PlatformService {
    /**
     * Platform
     * Enum — a fixed set of named constants.
     *
     * @constructor Creates a new Platform
     */
    enum class Platform {
        /**
         * Neoforge
         *
         * @constructor Creates a new NEOFORGE
         */
        NEOFORGE,

        /**
         * Forge
         *
         * @constructor Creates a new FORGE
         */
        FORGE,
    }

    /**
     * Environment
     * Enum — a fixed set of named constants.
     *
     * @constructor Creates a new Environment
     */
    enum class Environment {
        /**
         * Server
         *
         * @constructor Creates a new SERVER
         */
        SERVER,

        /**
         * Client
         *
         * @constructor Creates a new CLIENT
         */
        CLIENT,
    }

    /**
     * Returns `true` if environment
     *
     * @param env
     * @return `true` if the condition holds, `false` otherwise
     */
    infix fun isEnvironment(env: Environment): Boolean = environment == env

    val currentPlatform: Platform

    /**
     * Physical dist
     */
    val environment: Environment

    /**
     * Logical side
     */
    val currentThreadSide: Environment

    /**
     * Returns `true` if mod loaded
     *
     * @param modId the mod id
     * @return `true` if the condition holds, `false` otherwise
     */
    fun isModLoaded(modId: String): Boolean

    val serverRootPath: Path

}

val platformService: PlatformService by lazy {
    loadService<PlatformService>()
}
