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
    addBooleanField(EnableCodeCompletion, "Enable simple, experimental, temporary code completion", c)

    val collectionAndArrayValuesGroupSize = new IntegerFieldEditor(CollectionAndArrayValuesGroupSize,
      "Number of collection elements shown in logical structure:", c)
    collectionAndArrayValuesGroupSize.setValidRange(1, Integer.MAX_VALUE)
    addField(collectionAndArrayValuesGroupSize)

    addBooleanField(ShowStaticFieldsInTreeView, "Show static fields in tree view", c)
    addBooleanField(ShowSyntheticFieldsInTreeView, "Show synthetic fields in tree view", c)
    addBooleanField(RefreshVariablesViewAfterEvaluation, "Refresh variables view after evaluation", c)
    addBooleanField(AddImportsFromCurrentFile, "Add imports from current file", c)
  }

  override def init(workbench: IWorkbench): Unit = {}

  override def dispose: Unit = {
    super.dispose()
  }

  private def addBooleanField(name: String, label: String, parent: Composite): Unit =
    addField(new BooleanFieldEditor(name, label, parent))
}

object ExprEvalPreferencePage {
  val EnableCodeCompletion = "org.scala-ide.sdt.debug.expression.expEval.codeCompletion"
  val ShowLineNumbers = "org.scala-ide.sdt.debug.expression.expEval.enableLineNumbersByDefault"
  val CollectionAndArrayValuesGroupSize = "org.scala-ide.sdt.debug.expression.expEval.arrayValuesGroupSize"
  val LayoutType = "org.scala-ide.sdt.debug.expression.expEval.layoutType"
  val ShowStaticFieldsInTreeView = "org.scala-ide.sdt.debug.expression.expEval.showStaticFieldsInTreeView"
  val ShowSyntheticFieldsInTreeView = "org.scala-ide.sdt.debug.expression.expEval.showSyntheticFieldsInTreeView"
  val ShowCollectionsLogicalStructure = "org.scala-ide.sdt.debug.expression.expEval.showCollectionsLogicalStructureInTreeView"
  val RefreshVariablesViewAfterEvaluation = "org.scala-ide.sdt.debug.expEval.refreshVariablesViewAfterEvaluation"
  val AddImportsFromCurrentFile = "org.scala-ide.sdt.debug.expression.expEval.addImportsFromCurrentFile"
}

class ExprEvalPreferencesInitializer extends AbstractPreferenceInitializer {
  import ExprEvalPreferencePage._

  override def initializeDefaultPreferences(): Unit = {
    val store = ScalaExpressionEvaluatorPlugin().getPreferenceStore

    store.setDefault(EnableCodeCompletion, false)
    store.setDefault(ShowLineNumbers, false)
    store.setDefault(ShowStaticFieldsInTreeView, true)
    store.setDefault(ShowSyntheticFieldsInTreeView, true)
    store.setDefault(CollectionAndArrayValuesGroupSize, 100)
    store.setDefault(LayoutType, ExpressionEvaluatorViewLayoutType.ConsoleAndTreeHorizontally.id.toString())
    store.setDefault(ShowCollectionsLogicalStructure, true)
    store.setDefault(RefreshVariablesViewAfterEvaluation, true)
    store.setDefault(AddImportsFromCurrentFile, false)
  }
}
