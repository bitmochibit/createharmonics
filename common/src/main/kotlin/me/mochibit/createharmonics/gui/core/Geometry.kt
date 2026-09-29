package me.mochibit.createharmonics.gui.core

data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
    val right: Int get() = x + width
    val bottom: Int get() = y + height

    fun contains(px: Double, py: Double): Boolean =
        px >= x && px <= right && py >= y && py <= bottom

    fun offset(dx: Int, dy: Int): Rect = Rect(x + dx, y + dy, width, height)


    fun intersect(other: Rect): Rect {
        val nx1 = maxOf(x, other.x)
        val ny1 = maxOf(y, other.y)
        val nx2 = minOf(right, other.right)
        val ny2 = minOf(bottom, other.bottom)
        return Rect(nx1, ny1, maxOf(0, nx2 - nx1), maxOf(0, ny2 - ny1))
    }
}

