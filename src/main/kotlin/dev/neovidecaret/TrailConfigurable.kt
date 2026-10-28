package dev.neovidecaret

import com.intellij.openapi.options.Configurable
import com.intellij.ui.ColorPanel
import java.awt.Color
import java.awt.GridLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class TrailConfigurable : Configurable {
    private var panel: JPanel? = null
    private lateinit var enabled: JCheckBox
    private lateinit var block: JCheckBox
    private lateinit var duration: JSpinner
    private lateinit var stretch: JSpinner
    private lateinit var fps: JSpinner
    private lateinit var opacity: JSpinner
    private lateinit var followColor: JCheckBox
    private lateinit var color: ColorPanel
    private lateinit var diagnostics: JCheckBox
    private lateinit var effects: JCheckBox

    override fun getDisplayName() = "mousechannelVide"
    override fun createComponent(): JComponent {
        enabled = JCheckBox("Enable cursor animation")
        block = JCheckBox("Block-shaped animated cursor")
        duration = JSpinner(SpinnerNumberModel(90, 30, 250, 10))
        stretch = JSpinner(SpinnerNumberModel(1.0, 0.0, 3.0, 0.1))
        fps = JSpinner(SpinnerNumberModel(60, 60, 120, 10))
        opacity = JSpinner(SpinnerNumberModel(75, 10, 100, 5))
        color = ColorPanel().apply { selectedColor = Color(0x78C8FF) }
        diagnostics = JCheckBox("Log animation performance diagnostics")
        effects = JCheckBox("Neon glow and gradient trail")
        followColor = JCheckBox("Follow editor caret color").apply {
            addActionListener { color.isEnabled = !isSelected }
        }
        val fields = JPanel(GridLayout(0, 2, 12, 8)).apply {
            add(enabled); add(block)
            add(JLabel("Animation duration (ms)")); add(duration)
            add(JLabel("Trail stretch")); add(stretch)
            add(JLabel("Maximum frames per second")); add(fps)
            add(JLabel("Opacity (%)")); add(opacity)
            add(followColor); add(JLabel())
            add(JLabel("Animation color")); add(color)
            add(diagnostics); add(JLabel())
            add(effects); add(JLabel())
        }
        panel = JPanel(java.awt.BorderLayout()).apply { add(fields, java.awt.BorderLayout.NORTH) }
        reset()
        return panel!!
    }

    private fun values() = TrailSettings.Options(
        enabled.isSelected, (duration.value as Number).toInt(), (stretch.value as Number).toDouble(),
        (fps.value as Number).toInt(), (opacity.value as Number).toInt(), block.isSelected,
        followColor.isSelected, (color.selectedColor ?: Color(0x78C8FF)).rgb and 0xFFFFFF,
        diagnostics.isSelected,
        effects.isSelected,
    )
    override fun isModified() = panel != null && values() != TrailSettings.get().getState()
    override fun apply() {
        TrailSettings.get().loadState(values())
        TrailEditorListener.resetAll()
    }
    override fun reset() {
        val s = TrailSettings.get().getState()
        enabled.isSelected = s.enabled; block.isSelected = s.blockCursor
        duration.value = s.durationMs.coerceIn(30, 250)
        stretch.value = s.stretch.coerceIn(0.0, 3.0)
        fps.value = s.fps.coerceIn(60, 120); opacity.value = s.opacity.coerceIn(10, 100)
        followColor.isSelected = s.followCaretColor
        color.selectedColor = Color(s.customColorRgb and 0xFFFFFF)
        color.isEnabled = !s.followCaretColor
        diagnostics.isSelected = s.performanceDiagnostics
        effects.isSelected = s.enhancedEffects
    }
    override fun disposeUIResources() { panel = null }
}
