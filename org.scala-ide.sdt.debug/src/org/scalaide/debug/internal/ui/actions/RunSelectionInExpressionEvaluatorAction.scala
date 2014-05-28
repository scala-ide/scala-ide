/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.ui.actions

import org.eclipse.core.resources.IProject
import org.eclipse.ui.IWorkbenchPage
import org.scalaide.debug.internal.ui.ExpressionEvaluatorView
import org.scalaide.ui.internal.actions.RunSelectionAction

/**
 * Thank to it it's possible to directly evaluate in evaluator text selected in Scala editor
 */
class RunSelectionInExpressionEvaluatorAction extends RunSelectionAction {
  override def doWithSelection(project: IProject, activePage: IWorkbenchPage, text: String): Unit =
    ExpressionEvaluatorView.evaluate(project, activePage, text)
}