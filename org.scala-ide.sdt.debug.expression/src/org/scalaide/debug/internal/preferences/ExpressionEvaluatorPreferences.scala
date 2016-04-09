/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.preferences

import org.scalaide.debug.internal.ui.ExpressionEvaluatorViewLayoutType
import org.scalaide.debug.internal.expression.ScalaExpressionEvaluatorPlugin

/**
 * Provides more comfortable way to access preference values related to expression evaluator
 */
object ExpressionEvaluatorPreferences {
  import ExprEvalPreferencePage._

  private lazy val preferenceStore = ScalaExpressionEvaluatorPlugin().getPreferenceStore()

  def collectionAndArrayValuesGroupSize: Int =
    preferenceStore.getInt(CollectionAndArrayValuesGroupSize)

  def layoutType: ExpressionEvaluatorViewLayoutType.Value =
    ExpressionEvaluatorViewLayoutType(preferenceStore.getString(LayoutType).toInt)

  def layoutType_=(layoutType: ExpressionEvaluatorViewLayoutType.Value): Unit =
    preferenceStore.setValue(LayoutType, layoutType.id.toString())

  def isCodeCompletionEnabled: Boolean =
    preferenceStore.getBoolean(EnableCodeCompletion)

  def showLineNumbers: Boolean =
    preferenceStore.getBoolean(ShowLineNumbers)

  def showLineNumbers_=(enabled: Boolean): Unit =
    preferenceStore.setValue(ShowLineNumbers, enabled)

  def showStaticFieldsInTreeView: Boolean =
    preferenceStore.getBoolean(ShowStaticFieldsInTreeView)

  def showSyntheticFieldsInTreeView: Boolean =
    preferenceStore.getBoolean(ShowSyntheticFieldsInTreeView)

  def showCollectionsLogicalStructure: Boolean =
    preferenceStore.getBoolean(ShowCollectionsLogicalStructure)

  def showCollectionsLogicalStructure_=(enabled: Boolean): Unit =
    preferenceStore.setValue(ShowCollectionsLogicalStructure, enabled)

  def shouldRefreshVariablesViewAfterEvaluation: Boolean =
    preferenceStore.getBoolean(RefreshVariablesViewAfterEvaluation)

  def shouldAddImportsFromCurrentFile: Boolean =
    preferenceStore.getBoolean(AddImportsFromCurrentFile)
}
