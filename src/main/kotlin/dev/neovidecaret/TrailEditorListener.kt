package dev.neovidecaret

import com.intellij.openapi.Disposable
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.util.Disposer

class TrailEditorListener : EditorFactoryListener {
    init { runtime() }
    override fun editorCreated(event: EditorFactoryEvent) = runtime().attach(event.editor)
    override fun editorReleased(event: EditorFactoryEvent) = runtime().release(event.editor)

    companion object {
        private fun runtime() = ApplicationManager.getApplication().getService(TrailRuntime::class.java)
        fun resetAll() = runtime().settingsChanged()
    }
}

class TrailPluginListener : DynamicPluginListener {
    override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
        if (pluginDescriptor.pluginId.idString == "dev.neovidecaret") {
            ApplicationManager.getApplication().getService(TrailRuntime::class.java)
        }
    }
}

@Service(Service.Level.APP)
class TrailRuntime : Disposable {
    private val controllers = LinkedHashMap<Editor, TrailController>()
    private var disposed = false

    init {
        // Hot installation must also attach editors created before the plugin loaded.
        ApplicationManager.getApplication().invokeLater {
            if (!disposed) EditorFactory.getInstance().allEditors.forEach { attach(it) }
        }
    }

    fun attach(editor: Editor) {
        if (disposed || editor.isDisposed || editor.editorKind != EditorKind.MAIN_EDITOR ||
            editor.isViewer || controllers.containsKey(editor)) return
        val controller = TrailController(editor)
        Disposer.register(this, controller)
        controllers[editor] = controller
    }

    fun release(editor: Editor) {
        controllers.remove(editor)?.let { Disposer.dispose(it) }
    }

    fun settingsChanged() { controllers.values.forEach { it.settingsChanged() } }

    override fun dispose() {
        disposed = true
        controllers.clear()
    }
}
