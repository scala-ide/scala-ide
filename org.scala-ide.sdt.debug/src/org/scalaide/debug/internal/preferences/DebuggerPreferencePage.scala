package org.scalaide.debug.internal.preferences

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.jface.preference._
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Group
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage
import org.scalaide.debug.internal.ScalaDebugPlugin
import org.scalaide.debug.internal.model.MethodClassifier

class DebuggerPreferencePage extends FieldEditorPreferencePage(FieldEditorPreferencePage.GRID) with IWorkbenchPreferencePage {
  import DebuggerPreferencePage._

  setPreferenceStore(ScalaDebugPlugin.plugin.getPreferenceStore)

  override def createFieldEditors() {
    val filtersSection = createGroupComposite("Configured step filters", getFieldEditorParent())
    addBooleanField(FILTER_SYNTHETIC, "Filter SYNTHETIC methods", filtersSection)
    addBooleanField(FILTER_GETTER, "Filter Scala getters", filtersSection)
    addBooleanField(FILTER_SETTER, "Filter Scala setters", filtersSection)
    addBooleanField(FILTER_DEFAULT_GETTER, "Filter getters for default parameters", filtersSection)
    addBooleanField(FILTER_FORWARDER, "Filter forwarder to trait methods", filtersSection)

    val expressionEvaluatorSection = createGroupComposite("Expression Evaluator", getFieldEditorParent())
    addBooleanField(EXP_EVAL_ENABLE_CODE_COMPLETION, "Enable simple, experimental code completion", expressionEvaluatorSection)
  }

  def init(workbench: IWorkbench) {}

  private def addBooleanField(name: String, label: String, group: Group): Unit =
    addField(new BooleanFieldEditor(name, label, group))

  private def createGroupComposite(text: String, parent: Composite): Group = {
    val g = new Group(parent, SWT.NONE)
    g.setText(text)
    g.setLayout(new GridLayout(1, true))
    g.setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, true, false))
    g
  }

}

object DebuggerPreferencePage {
  import MethodClassifier._

  val BASE = ScalaDebugPlugin.id + "."
  val BASE_FILTER = BASE + "filter."
  val FILTER_SYNTHETIC = BASE_FILTER + Synthetic
  val FILTER_GETTER = BASE_FILTER + Getter
  val FILTER_SETTER = BASE_FILTER + Setter
  val FILTER_DEFAULT_GETTER = BASE_FILTER + DefaultGetter
  val FILTER_FORWARDER = BASE_FILTER + Forwarder

  val EXP_EVAL_ENABLE_CODE_COMPLETION = BASE + "expEval.codeCompletion"
}

class DebugerPreferencesInitializer extends AbstractPreferenceInitializer {
  import DebuggerPreferencePage._

  override def initializeDefaultPreferences() {
    val store = ScalaDebugPlugin.plugin.getPreferenceStore
    store.setDefault(FILTER_SYNTHETIC, true)
    store.setDefault(FILTER_GETTER, true)
    store.setDefault(FILTER_SETTER, true)
    store.setDefault(FILTER_DEFAULT_GETTER, true)
    store.setDefault(FILTER_FORWARDER, true)

    store.setDefault(EXP_EVAL_ENABLE_CODE_COMPLETION, true)
  }
}