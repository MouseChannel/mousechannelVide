package dev.neovidecaret

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(name = "NeovideCaret", storages = [Storage("neovide-caret.xml")])
class TrailSettings : PersistentStateComponent<TrailSettings.Options> {
    data class Options(
        var enabled: Boolean = true,
        var durationMs: Int = 90,
        var stretch: Double = 1.0,
        var fps: Int = 60,
        var opacity: Int = 75,
        var blockCursor: Boolean = true,
        var followCaretColor: Boolean = true,
        var customColorRgb: Int = 0x78C8FF,
        var performanceDiagnostics: Boolean = false,
        var enhancedEffects: Boolean = true,
    )

    private var options = Options()
    override fun getState() = options
    override fun loadState(state: Options) { options = state }

    companion object {
        fun get() = ApplicationManager.getApplication().getService(TrailSettings::class.java)
    }
}
