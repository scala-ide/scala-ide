package scala.tools.eclipse.debug.preferences

import org.eclipse.jface.preference._
import org.eclipse.ui.IWorkbenchPreferencePage
import org.eclipse.ui.IWorkbench
import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.swt.widgets.Link
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.jdt.internal.ui.preferences.PreferencesMessages
import org.eclipse.swt.SWT
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.ui.dialogs.PreferencesUtil
import scala.tools.eclipse.debug.ScalaDebugger
import scala.tools.eclipse.debug.ScalaDebugPlugin
import org.eclipse.swt.widgets.Label
import scala.tools.eclipse.debug.model.MethodClassifier

class DebuggerPreferences extends FieldEditorPreferencePage with IWorkbenchPreferencePage {
  import DebuggerPreferences._

  setPreferenceStore(ScalaDebugPlugin.plugin.getPreferenceStore)
  setDescription("""
Configured step filters:
  """)

  override def createFieldEditors() {
    val parent = getFieldEditorParent
    addField(new BooleanFieldEditor(FILTER_SYNTHETIC, "Filter SYNTHETIC methods", parent))
    addField(new BooleanFieldEditor(FILTER_GETTER, "Filter Scala getters", getFieldEditorParent))
    addField(new BooleanFieldEditor(FILTER_SETTER, "Filter Scala setters", getFieldEditorParent))
    addField(new BooleanFieldEditor(FILTER_DEFAULT_GETTER, "Filter getters for default parameters", getFieldEditorParent))
    addField(new BooleanFieldEditor(FILTER_FORWARDER, "Filter forwarder to trait methods", getFieldEditorParent))
  }

  def init(workbench: IWorkbench) {}

}

object DebuggerPreferences {
  import MethodClassifier._

  val BASE = ScalaDebugPlugin.id + "."
  val BASE_FILTER = BASE + "filter."
  val FILTER_SYNTHETIC = BASE_FILTER + Synthetic
  val FILTER_GETTER = BASE_FILTER + Getter
  val FILTER_SETTER = BASE_FILTER + Setter
  val FILTER_DEFAULT_GETTER = BASE_FILTER + DefaultGetter
  val FILTER_FORWARDER = BASE_FILTER + Forwarder
}

class DebugerPreferencesInitializer extends AbstractPreferenceInitializer {
  import DebuggerPreferences._

  override def initializeDefaultPreferences() {
    val store = ScalaDebugPlugin.plugin.getPreferenceStore
    store.setDefault(FILTER_SYNTHETIC, true)
    store.setDefault(FILTER_GETTER, true)
    store.setDefault(FILTER_SETTER, true)
    store.setDefault(FILTER_DEFAULT_GETTER, true)
    store.setDefault(FILTER_FORWARDER, true)
  }
}