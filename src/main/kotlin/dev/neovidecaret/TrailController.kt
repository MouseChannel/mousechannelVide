package dev.neovidecaret

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.CaretVisualAttributes
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.ui.AbstractPainter
import com.intellij.openapi.wm.IdeGlassPaneUtil
import java.awt.Color
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.Point
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.event.InputMethodEvent
import java.awt.event.InputMethodListener
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class TrailController(private val editor: Editor) : Disposable {
    private val content = editor.contentComponent
    private val motion = CornerMotion()
    private val effects = TrailEffects()
    private var targetX = 0.0
    private var targetY = 0.0
    private var targetWidth = 2.0
    private var targetHeight = 20.0
    private val bounds = Rectangle()
    private val dirty = Rectangle()
    private val clipped = Rectangle()
    private val viewport = Rectangle(editor.scrollingModel.visibleArea)
    private val origin = Point()
    private val settingsService = TrailSettings.get()
    private var painterLifetime: Disposable? = null
    private var painterRoot: Component? = null
    private var diagnostics = false
    private var frameCount = 0
    private var paintCount = 0
    private var maxTickNs = 0L
    private var maxPaintNs = 0L
    private var maxRepaintRatio = 0.0
    private var frameIntervals = 0
    private var intervalNs = 0L
    private var maxIntervalNs = 0L
    private var lateFrames = 0
    private val frameClock = FrameClock()
    private var active = false
    private var disposed = false
    private var composing = false
    private var lastTick = 0L
    private var hiddenCaret: Caret? = null
    private var original: CaretVisualAttributes? = null
    private var hidden: CaretVisualAttributes? = null
    private var color = Color.WHITE
    private val timer = Timer(16) { tick() }.apply { isCoalesce = true; isRepeats = false }

    private val painter = object : AbstractPainter() {
        override fun needsRepaint() = active
        override fun executePaint(component: Component, g: Graphics2D) {
            if (!active || !eligible()) return
            val start = if (diagnostics) System.nanoTime() else 0L
            val savedClip = g.clip
            val savedColor = g.color
            val savedComposite = g.composite
            try {
                g.clip(viewport)
                if (!g.hitClip(bounds.x, bounds.y, bounds.width, bounds.height)) return
                effects.paint(g, motion)
            } finally {
                g.clip = savedClip; g.color = savedColor; g.composite = savedComposite
                if (diagnostics) {
                    paintCount++
                    maxPaintNs = max(maxPaintNs, System.nanoTime() - start)
                }
            }
        }
    }

    private val focus = object : FocusAdapter() {
        override fun focusLost(e: FocusEvent) { composing = false; reset(); updatePainter() }
        override fun focusGained(e: FocusEvent) { reset(); updatePainter() }
    }
    private val hierarchy = HierarchyListener { e ->
        if (e.changeFlags and (HierarchyEvent.SHOWING_CHANGED or HierarchyEvent.PARENT_CHANGED).toLong() != 0L) {
            reset()
            updatePainter()
        }
    }
    private val input = object : InputMethodListener {
        override fun inputMethodTextChanged(event: InputMethodEvent) {
            val text = event.text
            composing = text != null && text.endIndex - text.beginIndex > event.committedCharacterCount
            reset()
        }
        override fun caretPositionChanged(event: InputMethodEvent) = Unit
    }

    init {
        content.addFocusListener(focus)
        content.addHierarchyListener(hierarchy)
        content.addInputMethodListener(input)
        editor.scrollingModel.addVisibleAreaListener({ event ->
            val next = event.newRectangle
            val point = editor.visualPositionToXY(editor.caretModel.primaryCaret.visualPosition)
            val keep = AnimationPolicy.canContinueScroll(viewport, next, point.x, point.y)
            viewport.setBounds(next)
            if (!keep) reset() else if (active) repaint(bounds)
        }, this)
        editor.selectionModel.addSelectionListener(object : SelectionListener {
            override fun selectionChanged(event: SelectionEvent) {
                if (editor.selectionModel.hasSelection()) reset()
            }
        }, this)
        editor.caretModel.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) = move()
            override fun caretAdded(event: CaretEvent) = reset()
            override fun caretRemoved(event: CaretEvent) = reset()
        }, this)
        SwingUtilities.invokeLater {
            if (!disposed && !editor.isDisposed) { reset(); updatePainter() }
        }
    }

    private fun updatePainter() {
        val root = content.rootPane?.glassPane
        val needed = !disposed && content.isShowing && content.isFocusOwner &&
            settingsService.getState().enabled && root != null
        if (painterLifetime != null && (!needed || painterRoot !== root)) {
            Disposer.dispose(painterLifetime!!)
            painterLifetime = null
            painterRoot = null
        }
        if (needed && painterLifetime == null) {
            val lifetime = Disposer.newDisposable("Neovide focused editor painter")
            Disposer.register(this, lifetime)
            IdeGlassPaneUtil.find(content).addPainter(content, painter, lifetime)
            painterLifetime = lifetime
            painterRoot = root
        }
    }

    private fun eligible() = !disposed && !editor.isDisposed && content.isShowing && content.isFocusOwner &&
        !composing && !editor.selectionModel.hasSelection() && editor.caretModel.caretCount == 1 &&
        settingsService.getState().enabled

    private fun cursorWidth(): Double = if (settingsService.getState().blockCursor) {
        max(2, content.getFontMetrics(editor.colorsScheme.getFont(com.intellij.openapi.editor.colors.EditorFontType.PLAIN)).charWidth('m')).toDouble()
    } else 2.0

    private fun move() {
        if (!eligible()) { reset(); return }
        val caret = editor.caretModel.primaryCaret
        val point = editor.visualPositionToXY(caret.visualPosition)
        val settings = settingsService.getState()
        val height = editor.lineHeight.toDouble()
        val width = cursorWidth()
        val fromX = (motion.x[0] + motion.x[2]) * 0.5
        val fromY = (motion.y[0] + motion.y[2]) * 0.5
        val snap = !motion.initialized || !viewport.contains(point)
        motion.target(point.x.toDouble(), point.y.toDouble(), width, height, snap)
        if (snap) { reset(); return }
        if (!active) {
            updatePainter()
            if (painterLifetime == null) { reset(); return }
            diagnostics = settings.performanceDiagnostics
            frameCount = 0; paintCount = 0; maxTickNs = 0; maxPaintNs = 0; maxRepaintRatio = 0.0
            frameIntervals = 0; intervalNs = 0; maxIntervalNs = 0; lateFrames = 0
            original = caret.visualAttributes
            hidden = CaretVisualAttributes(original!!.color, original!!.weight, CaretVisualAttributes.Shape.BAR, 0f)
            color = if (settings.followCaretColor) {
                original!!.color ?: editor.colorsScheme.getColor(EditorColors.CARET_COLOR) ?: Color.WHITE
            } else Color(settings.customColorRgb and 0xFFFFFF)
            effects.configure(color, settings.opacity, settings.enhancedEffects)
            hiddenCaret = caret
            active = true
            caret.visualAttributes = hidden!!
            updateBounds()
            lastTick = System.nanoTime()
            frameClock.start(lastTick, settings.fps)
        }
        targetX = point.x.toDouble(); targetY = point.y.toDouble()
        targetWidth = width; targetHeight = height
        effects.aim(fromX, fromY, targetX + width * 0.5, targetY + height * 0.5)
        if (!timer.isRunning) scheduleFrame()
    }

    private fun scheduleFrame() {
        timer.initialDelay = frameClock.delayMs(System.nanoTime())
        timer.start()
    }

    private fun tick() {
        val start = if (diagnostics) System.nanoTime() else 0L
        try { advanceFrame() } finally {
            if (diagnostics) {
                frameCount++
                maxTickNs = max(maxTickNs, System.nanoTime() - start)
            }
            if (active) {
                frameClock.advance(System.nanoTime())
                scheduleFrame()
            }
        }
    }

    private fun advanceFrame() {
        if (!eligible() || hiddenCaret?.visualAttributes !== hidden) { reset(); return }
        val now = System.nanoTime()
        val dt = (now - lastTick) / 1_000_000_000.0
        if (diagnostics) {
            val interval = now - lastTick
            frameIntervals++
            intervalNs += interval
            maxIntervalNs = max(maxIntervalNs, interval)
            if (interval > 25_000_000L) lateFrames++
        }
        lastTick = now
        if (dt > 0.12) { reset(); return }
        dirty.setBounds(bounds)
        val settings = settingsService.getState()
        val moving = motion.step(dt, settings.durationMs.coerceIn(30, 250) / 1000.0, settings.stretch.coerceIn(0.0, 3.0))
        updateBounds()
        dirty.add(bounds)
        if (!moving) {
            reset()
            repaint(dirty)
            return
        }
        repaint(dirty)
    }

    private fun updateBounds() {
        var x0 = motion.outlineX[0]; var x1 = x0
        var y0 = motion.outlineY[0]; var y1 = y0
        for (i in 1..3) {
            x0 = min(x0, motion.outlineX[i]); x1 = max(x1, motion.outlineX[i])
            y0 = min(y0, motion.outlineY[i]); y1 = max(y1, motion.outlineY[i])
        }
        val padding = if (effects.enhanced) TrailEffects.EFFECT_PADDING else 2
        bounds.setBounds(floor(x0).toInt() - padding, floor(y0).toInt() - padding,
            ceil(x1 - x0).toInt() + padding * 2 + 1, ceil(y1 - y0).toInt() + padding * 2 + 1)
    }

    private fun repaint(rect: Rectangle) {
        if (!content.isShowing) return
        val glass = content.rootPane?.glassPane ?: return
        if (!AnimationPolicy.clipInto(rect, viewport, clipped)) return
        if (diagnostics) {
            val area = viewport.width.toDouble() * viewport.height
            if (area > 0) maxRepaintRatio = max(maxRepaintRatio, clipped.width.toDouble() * clipped.height / area)
        }
        origin.setLocation(0, 0)
        SwingUtilities.convertPointToScreen(origin, content)
        SwingUtilities.convertPointFromScreen(origin, glass)
        glass.repaint(clipped.x + origin.x, clipped.y + origin.y, clipped.width, clipped.height)
    }

    fun reset() {
        timer.stop()
        val wasActive = active
        active = false
        restoreCaret()
        if (wasActive && !editor.isDisposed) repaint(bounds)
        if (!editor.isDisposed && !disposed) {
            val point = editor.visualPositionToXY(editor.caretModel.primaryCaret.visualPosition)
            motion.target(point.x.toDouble(), point.y.toDouble(), cursorWidth(), editor.lineHeight.toDouble(), true)
        }
        if (wasActive && diagnostics) {
            LOG.info("mousechannelVide frames=$frameCount paints=$paintCount maxTickUs=${maxTickNs / 1000} " +
                "maxPaintUs=${maxPaintNs / 1000} maxDirtyViewportPercent=${(maxRepaintRatio * 100).toInt()} " +
                "tickFps=${if (intervalNs > 0) frameIntervals * 1_000_000_000L / intervalNs else 0} " +
                "maxIntervalUs=${maxIntervalNs / 1000} intervalsOver25ms=$lateFrames")
        }
    }

    private fun restoreCaret() {
        val caret = hiddenCaret
        if (caret != null && caret.isValid && caret.visualAttributes === hidden) {
            original?.let { caret.visualAttributes = it }
        }
        hiddenCaret = null; original = null; hidden = null
    }

    fun settingsChanged() { reset(); updatePainter() }

    override fun dispose() {
        reset()
        disposed = true
        content.removeFocusListener(focus)
        content.removeHierarchyListener(hierarchy)
        content.removeInputMethodListener(input)
    }

    companion object {
        private val LOG = Logger.getInstance(TrailController::class.java)
    }
}
