package me.mochibit.createharmonics.gui.core

/**
 * Rect
 * Data class — a value holder; `equals`, `hashCode`, `toString` and `copy` are generated.
 *
 * @constructor Creates a new Rect
 * @property x
 * @property y
 * @property width
 * @property height
 */
data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
    val right: Int get() = x + width
    val bottom: Int get() = y + height

    /**
     * Returns `true` if
     *
     * @param px
     * @param py
     * @return `true` if the condition holds, `false` otherwise
     */
    fun contains(px: Double, py: Double): Boolean =
        px >= x && px <= right && py >= y && py <= bottom

    /**
     * Offset
     *
     * @param dx
     * @param dy
     * @return the rect
     */
    fun offset(dx: Int, dy: Int): Rect = Rect(x + dx, y + dy, width, height)


    /**
     * Intersect
     *
     * @param other
     * @return the rect
     */
    fun intersect(other: Rect): Rect {
        val nx1 = maxOf(x, other.x)
        val ny1 = maxOf(y, other.y)
        val nx2 = minOf(right, other.right)
        val ny2 = minOf(bottom, other.bottom)
        return Rect(nx1, ny1, maxOf(0, nx2 - nx1), maxOf(0, ny2 - ny1))
    }
}

