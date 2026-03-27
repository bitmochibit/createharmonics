package me.mochibit.createharmonics.foundation.eventbus

import kotlin.reflect.KClass

abstract class PlatformEventBridge {
    enum class ProxyEventType {
        SERVER_STARTED,
        SERVER_STOPPED,
        ENTITY_JOIN_LEVEL,
        PLAYER_START_TRACKING,
        REGISTER_COMMANDS,
        LEVEL_UNLOAD,
        GAME_SHUTTING_DOWN,
        CLIENT_DISCONNECTED,
        CLIENT_TICK,
    }

    protected abstract fun setupProxyEvents()

    fun setup() {
        setupProxyEvents()
    }
}
