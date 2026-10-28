package dev.neovidecaret

import org.junit.Assert.*
import org.junit.Test
import java.awt.Rectangle

class AnimationPolicyTest {
    @Test fun `small scrolling and resizing preserve visible cursor animation`() {
        val old = Rectangle(0, 0, 1000, 600)
        assertTrue(AnimationPolicy.canContinueScroll(old, Rectangle(30, 20, 1000, 600), 900, 40))
        assertTrue(AnimationPolicy.canContinueScroll(old, Rectangle(0, 0, 800, 500), 600, 40))
        assertFalse(AnimationPolicy.canContinueScroll(old, Rectangle(0, 400, 1000, 600), 600, 500))
        assertFalse(AnimationPolicy.canContinueScroll(old, Rectangle(30, 20, 1000, 600), 0, 0))
    }

    @Test fun `dirty region clips in place with nonzero scroll offsets`() {
        val result = Rectangle()
        assertTrue(AnimationPolicy.clipInto(Rectangle(50, 70, 100, 100), Rectangle(100, 100, 300, 200), result))
        assertEquals(Rectangle(100, 100, 50, 70), result)
        assertFalse(AnimationPolicy.clipInto(Rectangle(0, 0, 20, 20), Rectangle(100, 100, 300, 200), result))
    }

    @Test fun `synchronizing cancelled motion makes next jump start at actual caret`() {
        val motion = CornerMotion()
        motion.target(0.0, 0.0, 10.0, 20.0, true)
        motion.target(100.0, 0.0, 10.0, 20.0, false)
        motion.step(0.016, 0.09, 1.0)
        motion.target(400.0, 200.0, 10.0, 20.0, true)
        motion.target(600.0, 200.0, 10.0, 20.0, false)
        assertEquals(400.0, motion.x[0], 0.0)
        assertTrue(motion.step(0.016, 0.09, 1.0))
        assertTrue(motion.x[0] > 400.0 && motion.x[0] < 600.0)
    }
}
