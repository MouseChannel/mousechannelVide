package dev.neovidecaret

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/** Four independently damped corners create a stretched, continuous cursor silhouette. */
class CornerMotion {
    val x = DoubleArray(4)
    val y = DoubleArray(4)
    val outlineX = DoubleArray(4)
    val outlineY = DoubleArray(4)
    private val tx = DoubleArray(4)
    private val ty = DoubleArray(4)
    private val lag = DoubleArray(4)
    private var directionX = 1.0
    private var directionY = 0.0
    var initialized = false
        private set

    fun target(left: Double, top: Double, width: Double, height: Double, snap: Boolean) {
        val oldX = (tx[0] + tx[2]) * 0.5
        val oldY = (ty[0] + ty[2]) * 0.5
        tx[0] = left; tx[1] = left + width; tx[2] = left + width; tx[3] = left
        ty[0] = top; ty[1] = top; ty[2] = top + height; ty[3] = top + height
        val dx = left + width * 0.5 - oldX
        val dy = top + height * 0.5 - oldY
        val distance = sqrt(dx * dx + dy * dy)
        if (distance > 0.001) {
            directionX = dx / distance
            directionY = dy / distance
        }
        val scale = max(1.0, abs(dx) * width + abs(dy) * height)
        for (i in 0..3) {
            val projection = (tx[i] - left - width * 0.5) * dx + (ty[i] - top - height * 0.5) * dy
            lag[i] = (0.5 - projection / scale).coerceIn(0.0, 1.0)
        }
        if (!initialized || snap) settle()
        initialized = true
    }

    fun step(dt: Double, duration: Double, stretch: Double): Boolean {
        var moving = false
        for (i in 0..3) {
            val tau = duration * (0.16 + lag[i] * stretch * 0.32)
            val k = 1.0 - exp(-dt / max(0.001, tau))
            x[i] += (tx[i] - x[i]) * k
            y[i] += (ty[i] - y[i]) * k
            if (abs(tx[i] - x[i]) > 0.15 || abs(ty[i] - y[i]) > 0.15) moving = true
        }
        if (!moving) settle() else updateOutline()
        return moving
    }

    private fun updateOutline() {
        var centerX = 0.0
        var centerY = 0.0
        var error = 0.0
        for (i in 0..3) {
            centerX += x[i] * 0.25
            centerY += y[i] * 0.25
            error = max(error, max(abs(tx[i] - x[i]), abs(ty[i] - y[i])))
        }
        val size = max(1.0, max(tx[1] - tx[0], ty[3] - ty[0]))
        val amount = (error * 2.0 / size).coerceIn(0.0, 1.0)
        val normalX = -directionY
        val normalY = directionX
        // Narrow vertical ribbons while smoothly restoring full cursor width on arrival.
        val verticalNarrowing = 1.0 - 0.55 * directionY * directionY * amount
        for (i in 0..3) {
            val cross = (x[i] - centerX) * normalX + (y[i] - centerY) * normalY
            val tailScale = 1.0 - lag[i] * lag[i] * 0.92 * amount
            val contraction = cross * (1.0 - tailScale * verticalNarrowing)
            outlineX[i] = x[i] - normalX * contraction
            outlineY[i] = y[i] - normalY * contraction
        }
    }

    fun distanceTo(left: Double, top: Double): Double {
        val dx = left - tx[0]
        val dy = top - ty[0]
        return sqrt(dx * dx + dy * dy)
    }

    fun settle() {
        for (i in 0..3) {
            x[i] = tx[i]; y[i] = ty[i]
            outlineX[i] = x[i]; outlineY[i] = y[i]
        }
    }
}
