package me.mochibit.createharmonics.gui.core

import net.minecraft.client.gui.GuiGraphics


/**
 * Executes [block] always and safely surrounded by push and pop pose.
 */
inline fun GuiGraphics.poseScoped(
    translateX: Float = 0f,
    translateY: Float = 0f,
    translateZ: Float = 0f,
    block: GuiGraphics.() -> Unit,
) {
    pose().pushPose()
    try {
        if (translateX != 0f || translateY != 0f || translateZ != 0f) {
            pose().translate(translateX, translateY, translateZ)
        }
        block()
    } finally {
        pose().popPose()
    }
}

inline fun GuiGraphics.layer(z: Float, block: GuiGraphics.() -> Unit) =
    poseScoped(translateZ = z, block = block)

class ScissorGuard {
    private val stack = ArrayDeque<Rect>()
    val current: Rect? get() = stack.lastOrNull()

    fun push(graphics: GuiGraphics, area: Rect): Rect {
        val effective = current?.intersect(area) ?: area
        stack.addLast(effective)
        graphics.enableScissor(effective.x, effective.y, effective.right, effective.bottom)
        return effective
    }

    fun pop(graphics: GuiGraphics) {
        check(stack.isNotEmpty()) { "ScissorGuard.pop() called without before calling push" }
        stack.removeLast()
        graphics.disableScissor()
    }
}


/**
 * This guard executes [block] and if the block defines a graphical element inside [area] bounds then it will be drawn
 *
 * Exactly as OpenGL's scissor works, it defines a rectangle, called scissor box ([area]) and while it's enabled
 * only what's inside the area can be updated to the screen.
 *
 */
inline fun GuiGraphics.scissorScoped(guard: ScissorGuard, area: Rect, block: GuiGraphics.() -> Unit) {
    guard.push(this, area)
    try {
        block()
    } finally {
        guard.pop(this)
    }
}
