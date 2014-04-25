/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.preferences

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.jface.preference.BooleanFieldEditor
import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.jface.preference.IntegerFieldEditor
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Group
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage
import org.scalaide.debug.internal.ScalaDebugPlugin
import org.scalaide.debug.internal.model.MethodClassifier
import org.scalaide.debug.internal.ui.ExpressionEvaluatorViewLayoutType

class DebuggerPreferencePage extends FieldEditorPreferencePage(FieldEditorPreferencePage.GRID) with IWorkbenchPreferencePage {
  import DebuggerPreferencePage._

  setPreferenceStore(ScalaDebugPlugin.plugin.getPreferenceStore)

  private val groups = scala.collection.mutable.MutableList[Group]()

  override def createFieldEditors(): Unit = {
    val filtersSection = createGroupComposite("Configured step filters", getFieldEditorParent())
    addBooleanField(FILTER_SYNTHETIC, "Filter SYNTHETIC methods", filtersSection)
    addBooleanField(FILTER_GETTER, "Filter Scala getters", filtersSection)
    addBooleanField(FILTER_SETTER, "Filter Scala setters", filtersSection)
    addBooleanField(FILTER_DEFAULT_GETTER, "Filter getters for default parameters", filtersSection)
    addBooleanField(FILTER_FORWARDER, "Filter forwarder to trait methods", filtersSection)

    val expressionEvaluatorSection = createGroupComposite("Expression Evaluator", getFieldEditorParent())
    addBooleanField(EXP_EVAL_ENABLE_CODE_COMPLETION, "Enable simple, experimental, temporary code completion", expressionEvaluatorSection)

    val collectionAndArrayValuesGroupSize = new IntegerFieldEditor(EXP_EVAL_COLLECTION_AND_ARRAY_VALUES_GROUP_SIZE,
      "Count of values loaded and grouped for arrays and collections when logical structure is shown", expressionEvaluatorSection)
    collectionAndArrayValuesGroupSize.setValidRange(1, Integer.MAX_VALUE)
    addField(collectionAndArrayValuesGroupSize)
    addBooleanField(EXP_EVAL_SHOW_STATIC_FIELDS_IN_TREE_VIEW, "Show static fields in tree view", expressionEvaluatorSection)
    addBooleanField(EXP_EVAL_SHOW_SYNTHETIC_FIELDS_IN_TREE_VIEW, "Show synthetic fields in tree view", expressionEvaluatorSection)
  }

  override def init(workbench: IWorkbench): Unit = {}

  override def dispose: Unit = {
    groups.foreach(_.dispose())
    super.dispose()
  }

  private def addBooleanField(name: String, label: String, group: Group): Unit =
    addField(new BooleanFieldEditor(name, label, group))

  private def createGroupComposite(text: String, parent: Composite, style: Int = SWT.NONE): Group = {
    val g = new Group(parent, style)
    g.setText(text)
    groups += g
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
  val EXP_EVAL_SHOW_LINE_NUMBERS = BASE_EXP_EVAL + "enableLineNumbersByDefault"
  val EXP_EVAL_COLLECTION_AND_ARRAY_VALUES_GROUP_SIZE = BASE_EXP_EVAL + "arrayValuesGroupSize"
  val EXP_EVAL_LAYOUT_TYPE = BASE_EXP_EVAL + "layoutType"
  val EXP_EVAL_SHOW_STATIC_FIELDS_IN_TREE_VIEW = BASE_EXP_EVAL + "showStaticFieldsInTreeView"
  val EXP_EVAL_SHOW_SYNTHETIC_FIELDS_IN_TREE_VIEW = BASE_EXP_EVAL + "showSyntheticFieldsInTreeView"
  val EXP_EVAL_SHOW_COLLECTIONS_LOGICAL_STRUCTURE = BASE_EXP_EVAL + "showCollectionsLogicalStructureInTreeView"
}

class DebugerPreferencesInitializer extends AbstractPreferenceInitializer {
  import DebuggerPreferencePage._

  override def initializeDefaultPreferences(): Unit = {
    val store = ScalaDebugPlugin.plugin.getPreferenceStore
    store.setDefault(FILTER_SYNTHETIC, true)
    store.setDefault(FILTER_GETTER, true)
    store.setDefault(FILTER_SETTER, true)
    store.setDefault(FILTER_DEFAULT_GETTER, true)
    store.setDefault(FILTER_FORWARDER, true)

    store.setDefault(EXP_EVAL_ENABLE_CODE_COMPLETION, false)
    store.setDefault(EXP_EVAL_SHOW_LINE_NUMBERS, false)
    store.setDefault(EXP_EVAL_SHOW_STATIC_FIELDS_IN_TREE_VIEW, true)
    store.setDefault(EXP_EVAL_SHOW_SYNTHETIC_FIELDS_IN_TREE_VIEW, true)
    store.setDefault(EXP_EVAL_COLLECTION_AND_ARRAY_VALUES_GROUP_SIZE, 100)
    store.setDefault(EXP_EVAL_LAYOUT_TYPE, ExpressionEvaluatorViewLayoutType.CONSOLE_AND_TREE_HORIZONTALLY.id.toString())
    store.setDefault(EXP_EVAL_SHOW_COLLECTIONS_LOGICAL_STRUCTURE, true)
  }
}