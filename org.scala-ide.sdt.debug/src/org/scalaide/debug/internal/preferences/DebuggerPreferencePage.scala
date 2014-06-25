package org.scalaide.debug.internal.preferences

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.jface.preference.BooleanFieldEditor
import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.jface.preference.RadioGroupFieldEditor
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Group
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage
import org.scalaide.debug.internal.ScalaDebugPlugin
import org.scalaide.debug.internal.model.MethodClassifier.DefaultGetter
import org.scalaide.debug.internal.model.MethodClassifier.Forwarder
import org.scalaide.debug.internal.model.MethodClassifier.Getter
import org.scalaide.debug.internal.model.MethodClassifier.Setter
import org.scalaide.debug.internal.model.MethodClassifier.Synthetic
import org.scalaide.debug.internal.ui.ExpressionEvaluatorViewLayoutType
import org.scalaide.debug.internal.ui.ExpressionEvaluatorViewLayoutType.CONSOLE_AND_TREE_HORIZONTALLY
import org.scalaide.debug.internal.ui.ExpressionEvaluatorViewLayoutType.CONSOLE_AND_TREE_VERTICALLY
import org.scalaide.debug.internal.ui.ExpressionEvaluatorViewLayoutType.INPUT_AND_TREE_HORIZONTALLY
import org.scalaide.debug.internal.ui.ExpressionEvaluatorViewLayoutType.INPUT_AND_TREE_VERTICALLY
import org.scalaide.debug.internal.ui.ExpressionEvaluatorViewLayoutType.ONLY_CONSOLE
import org.scalaide.debug.internal.model.MethodClassifier
import org.eclipse.jface.preference.IntegerFieldEditor

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
    addBooleanField(EXP_EVAL_SHOW_LINE_NUMBERS_BY_DEFAULT, "Show line numbers by default", expressionEvaluatorSection)

    val arrayValuesGrroupSize = new IntegerFieldEditor(EXP_EVAL_ARRAY_VALUES_GROUP_SIZE,
      "Arrays: size of values group and maximum count of values shown without creating groups", expressionEvaluatorSection)
    arrayValuesGrroupSize.setValidRange(1, Integer.MAX_VALUE)
    addField(arrayValuesGrroupSize)

    import ExpressionEvaluatorViewLayoutType._
    addField(new RadioGroupFieldEditor(EXP_EVAL_DEFAULT_LAYOUT, "Default layout for expression evaluator view", 1,
      Array(
        Array(ONLY_CONSOLE.toString(), ONLY_CONSOLE.id.toString()),
        Array(CONSOLE_AND_TREE_HORIZONTALLY.toString(), CONSOLE_AND_TREE_HORIZONTALLY.id.toString()),
        Array(CONSOLE_AND_TREE_VERTICALLY.toString(), CONSOLE_AND_TREE_VERTICALLY.id.toString()),
        Array(INPUT_AND_TREE_HORIZONTALLY.toString(), INPUT_AND_TREE_HORIZONTALLY.id.toString()),
        Array(INPUT_AND_TREE_VERTICALLY.toString(), INPUT_AND_TREE_VERTICALLY.id.toString())),
      expressionEvaluatorSection))
  }

  def init(workbench: IWorkbench) {}

  private def addBooleanField(name: String, label: String, group: Group): Unit =
    addField(new BooleanFieldEditor(name, label, group))

  private def createGroupComposite(text: String, parent: Composite, style: Int = SWT.NONE): Group = {
    val g = new Group(parent, style)
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

  val BASE_EXP_EVAL = BASE + "expEval."
  val EXP_EVAL_ENABLE_CODE_COMPLETION = BASE_EXP_EVAL + "codeCompletion"
  val EXP_EVAL_SHOW_LINE_NUMBERS_BY_DEFAULT = BASE_EXP_EVAL + "enableLineNumbersByDefault"
  val EXP_EVAL_ARRAY_VALUES_GROUP_SIZE = BASE_EXP_EVAL + "arrayValuesGroupSize"
  val EXP_EVAL_DEFAULT_LAYOUT = BASE_EXP_EVAL + "defaultLayout"
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
    store.setDefault(EXP_EVAL_SHOW_LINE_NUMBERS_BY_DEFAULT, false)
    store.setDefault(EXP_EVAL_ARRAY_VALUES_GROUP_SIZE, 100)
    store.setDefault(EXP_EVAL_DEFAULT_LAYOUT, ExpressionEvaluatorViewLayoutType.ONLY_CONSOLE.id.toString())
  }
}