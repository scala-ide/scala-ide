/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.preferences

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.jface.preference.BooleanFieldEditor
import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.jface.preference.IntegerFieldEditor
import org.eclipse.swt.widgets.Composite
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage
import org.scalaide.debug.internal.ui.ExpressionEvaluatorViewLayoutType
import org.scalaide.debug.internal.expression.ScalaExpressionEvaluatorPlugin
import org.eclipse.swt.layout.GridLayout

class ExprEvalPreferencePage extends FieldEditorPreferencePage(FieldEditorPreferencePage.GRID) with IWorkbenchPreferencePage {
  import ExprEvalPreferencePage._

  setPreferenceStore(ScalaExpressionEvaluatorPlugin().getPreferenceStore)

  override def createFieldEditors(): Unit = {
    val c = getFieldEditorParent
    c.setLayout(new GridLayout(1, false))
    addBooleanField(EXP_EVAL_ENABLE_CODE_COMPLETION, "Enable simple, experimental, temporary code completion", c)

    val collectionAndArrayValuesGroupSize = new IntegerFieldEditor(EXP_EVAL_COLLECTION_AND_ARRAY_VALUES_GROUP_SIZE,
      "Number of collection elements shown in logical structure:", c)
    collectionAndArrayValuesGroupSize.setValidRange(1, Integer.MAX_VALUE)
    addField(collectionAndArrayValuesGroupSize)

    addBooleanField(EXP_EVAL_SHOW_STATIC_FIELDS_IN_TREE_VIEW, "Show static fields in tree view", c)
    addBooleanField(EXP_EVAL_SHOW_SYNTHETIC_FIELDS_IN_TREE_VIEW, "Show synthetic fields in tree view", c)
  }

  override def init(workbench: IWorkbench): Unit = {}

  override def dispose: Unit = {
    super.dispose()
  }

  private def addBooleanField(name: String, label: String, parent: Composite): Unit =
    addField(new BooleanFieldEditor(name, label, parent))
}

object ExprEvalPreferencePage {
  val BASE = "org.scala-ide.sdt.debug.expression."
  val BASE_EXP_EVAL = BASE + "expEval."
  val EXP_EVAL_ENABLE_CODE_COMPLETION = BASE_EXP_EVAL + "codeCompletion"
  val EXP_EVAL_SHOW_LINE_NUMBERS = BASE_EXP_EVAL + "enableLineNumbersByDefault"
  val EXP_EVAL_COLLECTION_AND_ARRAY_VALUES_GROUP_SIZE = BASE_EXP_EVAL + "arrayValuesGroupSize"
  val EXP_EVAL_LAYOUT_TYPE = BASE_EXP_EVAL + "layoutType"
  val EXP_EVAL_SHOW_STATIC_FIELDS_IN_TREE_VIEW = BASE_EXP_EVAL + "showStaticFieldsInTreeView"
  val EXP_EVAL_SHOW_SYNTHETIC_FIELDS_IN_TREE_VIEW = BASE_EXP_EVAL + "showSyntheticFieldsInTreeView"
  val EXP_EVAL_SHOW_COLLECTIONS_LOGICAL_STRUCTURE = BASE_EXP_EVAL + "showCollectionsLogicalStructureInTreeView"
}

class ExprEvalPreferencesInitializer extends AbstractPreferenceInitializer {
  import ExprEvalPreferencePage._

  override def initializeDefaultPreferences(): Unit = {
    val store = ScalaExpressionEvaluatorPlugin().getPreferenceStore

    store.setDefault(EXP_EVAL_ENABLE_CODE_COMPLETION, false)
    store.setDefault(EXP_EVAL_SHOW_LINE_NUMBERS, false)
    store.setDefault(EXP_EVAL_SHOW_STATIC_FIELDS_IN_TREE_VIEW, true)
    store.setDefault(EXP_EVAL_SHOW_SYNTHETIC_FIELDS_IN_TREE_VIEW, true)
    store.setDefault(EXP_EVAL_COLLECTION_AND_ARRAY_VALUES_GROUP_SIZE, 100)
    store.setDefault(EXP_EVAL_LAYOUT_TYPE, ExpressionEvaluatorViewLayoutType.CONSOLE_AND_TREE_HORIZONTALLY.id.toString())
    store.setDefault(EXP_EVAL_SHOW_COLLECTIONS_LOGICAL_STRUCTURE, true)
  }
}
