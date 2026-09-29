package me.mochibit.createharmonics.gui.core

import net.createmod.catnip.animation.LerpedFloat
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.util.Mth
import kotlin.math.max

class ScrollArea(
    private val bounds: Rect,
    private val scissorGuard: ScissorGuard,
    private val scrollStep: Float = 12f,
) {
    private val scroll = LerpedFloat.linear().startWithValue(0.0)
    private var lastOffset = 0f

    var contentHeight = 0
        private set

    val maxScroll: Int get() = max(0, contentHeight - bounds.height)

    fun tick() {
        scroll.tickChaser()
        val max = maxScroll.toFloat()
        if (scroll.chaseTarget > max || scroll.value > max) {
            scroll.chase(max.toDouble(), 0.7, LerpedFloat.Chaser.EXP)
        }
    }


    fun contains(mouseX: Double, mouseY: Double): Boolean =
        mouseX >= bounds.x && mouseX < bounds.x + bounds.width &&
                mouseY >= bounds.y && mouseY < bounds.y + bounds.height

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
        if (!contains(mouseX, mouseY) || maxScroll == 0) return false
        val target = Mth.clamp(scroll.chaseTarget - (scrollY * scrollStep).toFloat(), 0f, maxScroll.toFloat())
        scroll.chase(target.toDouble(), 0.7, LerpedFloat.Chaser.EXP)
        return true
    }

    inline fun mouseClicked(
        mouseX: Double,
        mouseY: Double,
        handler: (localX: Double, localY: Double) -> Boolean,
    ): Boolean = contains(mouseX, mouseY) && handler(toLocalX(mouseX), toLocalY(mouseY))

    fun toLocalX(screenX: Double): Double = screenX - bounds.x
    fun toLocalY(screenY: Double): Double = screenY - bounds.y - lastOffset
    fun toScreenY(localY: Int): Int = (bounds.y + lastOffset + localY).toInt()

    fun render(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTicks: Float,
        content: ScrollScope.() -> Int,
    ) {
        lastOffset = -scroll.getValue(partialTicks).coerceAtMost(maxScroll.toFloat())

        val hovering = contains(mouseX.toDouble(), mouseY.toDouble())
        val scope = ScrollScope(
            graphics = graphics,
            width = bounds.width,
            height = bounds.height,
            mouseX = if (hovering) toLocalX(mouseX.toDouble()).toInt() else NO_MOUSE,
            mouseY = if (hovering) toLocalY(mouseY.toDouble()).toInt() else NO_MOUSE,
            partialTicks = partialTicks,
            originX = bounds.x,
            originY = bounds.y,
            scrollOffset = lastOffset,
            clip = bounds
        )

        graphics.scissorScoped(scissorGuard, bounds) {
            graphics.pose().pushPose()
            graphics.pose().translate(bounds.x.toFloat(), bounds.y + lastOffset, 0f)
            contentHeight = scope.content()
            graphics.pose().popPose()
        }
    }

    private companion object {
        const val NO_MOUSE = -10_000
    }
}

class ScrollScope(
    val graphics: GuiGraphics,
    val originX: Int,          
    val originY: Int,
    val scrollOffset: Float,
    val width: Int,
    val height: Int,
    val mouseX: Int,
    val mouseY: Int,
    val partialTicks: Float,
    val clip: Rect,
) {

    inline fun layer(z: Float, block: () -> Unit) {
        graphics.pose().pushPose()
        graphics.pose().translate(0f, 0f, z)
        block()
        graphics.pose().popPose()
    }
}
