package dev.neovidecaret

import org.junit.Assert.*
import org.junit.Test

class CornerMotionTest {
    @Test fun `vertical jumps narrow leading edge and restore width on arrival`() {
        for (sign in listOf(-1, 1)) {
            val m = CornerMotion()
            m.target(0.0, 0.0, 10.0, 20.0, true)
            m.target(0.0, sign * 100.0, 10.0, 20.0, false)
            m.step(0.016, 0.09, 1.0)
            val a = if (sign > 0) 2 else 0
            val b = if (sign > 0) 3 else 1
            val width = kotlin.math.abs(m.outlineX[a] - m.outlineX[b])
            assertEquals(4.5, width, 0.01)
            repeat(120) { m.step(1.0 / 60, 0.09, 1.0) }
            assertEquals(10.0, kotlin.math.abs(m.outlineX[a] - m.outlineX[b]), 0.0)
        }
    }

    @Test fun `tail narrows across horizontal and vertical motion in both directions`() {
        for (axis in 0..1) for (sign in listOf(-1, 1)) {
            val m = CornerMotion()
            m.target(0.0, 0.0, 10.0, 20.0, true)
            m.target(if (axis == 0) sign * 100.0 else 0.0,
                if (axis == 1) sign * 100.0 else 0.0, 10.0, 20.0, false)
            m.step(0.016, 0.09, 1.0)
            val leading = when {
                axis == 0 && sign > 0 -> 1 to 2
                axis == 0 -> 0 to 3
                sign > 0 -> 2 to 3
                else -> 0 to 1
            }
            val trailing = when {
                axis == 0 && sign > 0 -> 0 to 3
                axis == 0 -> 1 to 2
                sign > 0 -> 0 to 1
                else -> 2 to 3
            }
            val cross = if (axis == 0) m.outlineY else m.outlineX
            val headWidth = kotlin.math.abs(cross[leading.first] - cross[leading.second])
            val tailWidth = kotlin.math.abs(cross[trailing.first] - cross[trailing.second])
            assertTrue("axis=$axis sign=$sign", tailWidth < headWidth * 0.2)
            m.settle()
            for (i in 0..3) {
                assertEquals(m.x[i], m.outlineX[i], 0.0)
                assertEquals(m.y[i], m.outlineY[i], 0.0)
            }
        }
    }

    @Test fun `motion is independent of frame subdivision`() {
        fun run(hz: Int): CornerMotion {
            val m = CornerMotion()
            m.target(0.0, 0.0, 10.0, 20.0, true)
            m.target(100.0, 40.0, 10.0, 20.0, false)
            repeat(hz / 10) { m.step(1.0 / hz, 0.09, 1.0) }
            return m
        }
        val slow = run(30); val fast = run(120)
        for (i in 0..3) {
            assertEquals(slow.x[i], fast.x[i], 0.001)
            assertEquals(slow.y[i], fast.y[i], 0.001)
        }
    }

    @Test fun `leading corners arrive first and all corners settle`() {
        val m = CornerMotion()
        m.target(0.0, 0.0, 10.0, 20.0, true)
        m.target(100.0, 0.0, 10.0, 20.0, false)
        m.step(0.016, 0.09, 1.0)
        assertTrue(m.x[1] - 10 > m.x[0])
        var moving = true
        repeat(120) { moving = m.step(1.0 / 60, 0.09, 1.0) }
        assertFalse(moving)
        assertEquals(100.0, m.x[0], 0.0)
        assertEquals(110.0, m.x[1], 0.0)
    }
}
