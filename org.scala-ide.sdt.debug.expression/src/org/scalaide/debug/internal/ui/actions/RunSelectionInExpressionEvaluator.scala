/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.ui.actions

import org.eclipse.core.resources.IProject
import org.eclipse.ui.IWorkbenchPage
import org.scalaide.debug.internal.ui.ExpressionEvaluatorView
import org.scalaide.ui.internal.actions.RunSelection

/**
 * Thank to it it's possible to directly evaluate in evaluator text selected in Scala editor
 */
class RunSelectionInExpressionEvaluator extends RunSelection {
  override def doWithSelection(project: IProject, activePage: IWorkbenchPage, text: String): Unit =
    ExpressionEvaluatorView.evaluate(project, activePage, text)
}