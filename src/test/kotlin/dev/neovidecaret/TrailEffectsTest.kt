package dev.neovidecaret

import org.junit.Assert.*
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage

class TrailEffectsTest {
    @Test fun `enhanced trail renders glow and restores graphics state`() {
        val image = BufferedImage(180, 80, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        val motion = CornerMotion()
        motion.target(20.0, 25.0, 10.0, 20.0, true)
        motion.target(120.0, 25.0, 10.0, 20.0, false)
        motion.step(0.016, 0.09, 1.0)
        val effects = TrailEffects()
        effects.configure(Color.CYAN, 75, true)
        effects.aim(25.0, 35.0, 125.0, 35.0)
        val paint = g.paint
        val composite = g.composite
        val stroke = g.stroke
        val hints = g.renderingHints
        effects.paint(g, motion)
        assertEquals(paint, g.paint)
        assertEquals(composite, g.composite)
        assertEquals(stroke, g.stroke)
        assertEquals(hints, g.renderingHints)
        assertTrue((0 until 180).any { x -> (0 until 80).any { y -> image.getRGB(x, y) ushr 24 > 0 } })
        g.dispose()
    }
}
