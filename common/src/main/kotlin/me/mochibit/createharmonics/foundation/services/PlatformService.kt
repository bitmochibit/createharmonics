package me.mochibit.createharmonics.foundation.services

interface PlatformService {
    enum class Environment {
        SERVER,
        CLIENT,
    }

    infix fun isEnvironment(env: Environment): Boolean = environment == env

    val platformName: String

    val environment: Environment

    fun isModLoaded(modId: String): Boolean

    fun setupEventBridge()
}

val platformService: PlatformService by lazy {
    loadService<PlatformService>()
}
