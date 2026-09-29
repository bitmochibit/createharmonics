package me.mochibit.createharmonics.gui.core

/**
 * Widget cache to avoid continuous redrawing of non correlated widgets
 */
class WidgetCache<K, W> {
    private var current = mutableMapOf<K, W>()
    private var next = mutableMapOf<K, W>()

    /**
     * This must be called when a widget rebuild is about to start
     */
    fun beginFrame() {
        next = mutableMapOf()
    }

    fun getOrCreate(key: K, factory: (K) -> W): W {
        val widget = current[key] ?: factory(key)
        next[key] = widget
        return widget
    }

    /**
     * This must be called AFTER a widget rebuild is complete, so that non-requested widgets will not be redrawn.
     */
    fun endFrame(onEvicted: (W) -> Unit = {}) {
        for ((key, widget) in current) {
            if (key !in next) onEvicted(widget)
        }
        current = next
    }

    fun values(): Collection<W> = current.values
}
