package dev.neovidecaret

import java.awt.Rectangle
import kotlin.math.abs

internal object AnimationPolicy {
    fun canContinueScroll(old: Rectangle, next: Rectangle, caretX: Int, caretY: Int): Boolean =
        next.contains(caretX, caretY) &&
            abs(next.x.toLong() - old.x) <= next.width / 2 &&
            abs(next.y.toLong() - old.y) <= next.height / 2

    fun clipInto(source: Rectangle, viewport: Rectangle, result: Rectangle): Boolean {
        val left = maxOf(source.x, viewport.x)
        val top = maxOf(source.y, viewport.y)
        val right = minOf(source.x + source.width, viewport.x + viewport.width)
        val bottom = minOf(source.y + source.height, viewport.y + viewport.height)
        result.setBounds(left, top, maxOf(0, right - left), maxOf(0, bottom - top))
        return !result.isEmpty
    }
}
