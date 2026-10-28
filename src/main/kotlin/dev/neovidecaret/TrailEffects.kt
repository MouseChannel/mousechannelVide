package dev.neovidecaret

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.Paint
import java.awt.RenderingHints
import java.awt.geom.Path2D

internal class TrailEffects {
    private val path = Path2D.Double(Path2D.WIND_NON_ZERO, 5)
    private val outerStroke = BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    private val innerStroke = BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    private val edgeStroke = BasicStroke(1f)
    private val alphas = Array(101) { AlphaComposite.getInstance(AlphaComposite.SRC_OVER, it / 100f) }
    private var gradient: Paint = Color.WHITE
    private var color = Color.WHITE
    private var highlight = Color.WHITE
    private var opacity = 75
    var enhanced = true
        private set

    fun configure(base: Color, opacityPercent: Int, enabled: Boolean) {
        color = base
        highlight = Color((base.red + 255) / 2, (base.green + 255) / 2, (base.blue + 255) / 2)
        opacity = opacityPercent.coerceIn(10, 100)
        enhanced = enabled
    }

    fun aim(fromX: Double, fromY: Double, toX: Double, toY: Double) {
        val hue = Color.RGBtoHSB(color.red, color.green, color.blue, null)
        val tail = Color.getHSBColor((hue[0] + 0.10f) % 1f, maxOf(0.45f, hue[1]), hue[2])
        gradient = GradientPaint(fromX.toFloat(), fromY.toFloat(), tail,
            toX.toFloat(), toY.toFloat(), highlight)
    }

    fun paint(g: Graphics2D, motion: CornerMotion) {
        val paint = g.paint
        val composite = g.composite
        val stroke = g.stroke
        val hints = g.renderingHints
        try {
            if (enhanced) g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            path.reset()
            path.moveTo(motion.outlineX[0], motion.outlineY[0])
            for (i in 1..3) path.lineTo(motion.outlineX[i], motion.outlineY[i])
            path.closePath()
            if (enhanced) {
                g.color = color
                g.stroke = outerStroke
                g.composite = alphas[opacity / 5]
                g.draw(path)
                g.stroke = innerStroke
                g.composite = alphas[opacity / 3]
                g.draw(path)
                g.paint = gradient
            } else g.color = color
            g.composite = alphas[opacity]
            g.fill(path)
            if (enhanced) {
                g.stroke = edgeStroke
                g.color = highlight
                g.composite = alphas[opacity / 3]
                g.draw(path)
            }
        } finally {
            g.paint = paint; g.composite = composite; g.stroke = stroke
            g.setRenderingHints(hints)
        }
    }

    companion object {
        const val EFFECT_PADDING = 5
    }
}
