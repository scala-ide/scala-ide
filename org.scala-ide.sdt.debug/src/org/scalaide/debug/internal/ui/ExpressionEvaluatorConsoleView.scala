/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.ui

import org.eclipse.core.resources.IProject
import org.eclipse.debug.internal.ui.DebugPluginImages
import org.eclipse.debug.internal.ui.IInternalDebugUIConstants
import org.eclipse.jface.action.Action
import org.eclipse.jface.action.Separator
import org.eclipse.jface.bindings.keys.KeyStroke
import org.eclipse.jface.fieldassist.ContentProposalAdapter
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.VerifyKeyListener
import org.eclipse.swt.events.ModifyEvent
import org.eclipse.swt.events.ModifyListener
import org.eclipse.swt.events.VerifyEvent
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.widgets.Composite
import org.eclipse.ui.IWorkbenchPage
import org.eclipse.ui.console.IConsoleConstants
import org.eclipse.ui.internal.console.ConsolePluginImages
import org.eclipse.ui.internal.console.IInternalConsoleConstants
import org.eclipse.ui.part.ViewPart
import org.scalaide.debug.internal.ScalaDebugPlugin
import org.scalaide.debug.internal.expression.ExpressionManager
import org.scalaide.debug.internal.preferences.DebuggerPreferencePage
import org.scalaide.debug.internal.ui.completion.SimpleContentProposalProvider
import org.scalaide.ui.internal.repl.InterpreterConsoleView

/**
 * UI component for debug expression evaluation.
 */
class ExpressionEvaluatorConsoleView extends ViewPart with InterpreterConsoleView {
  // TODO it would be nice to add support for undo and redo (ctrl+z / ctrl+y)

  private val resultCallback = displayOutput _
  private val errorCallback = displayError _
  private val codeCompletionPopupWidth = 512
  private val codeCompletionPopupHeight = 320

  private lazy val prefStore = ScalaDebugPlugin.plugin.getPreferenceStore()

  override def evaluate(text: String) {
    displayCode(text)
    ExpressionManager.compute(text, resultCallback, errorCallback)
  }

  override def createPartControl(parent: Composite) {
    createInterpreterPartControl(parent)
  }

  override def createInterpreterPartControl(parent: Composite) {
    super.createInterpreterPartControl(parent)
    val toolbarManager = getViewSite.getActionBars.getToolBarManager
    toolbarManager.add(evaluateAction)
    toolbarManager.add(clearOutputAction)
    toolbarManager.add(new Separator)
    toolbarManager.add(clearConsoleAndHistoryAction)

    setPartName("Scala Expression Evaluator")

    val provider = new SimpleContentProposalProvider {
      override protected def isCodeCompletionEnabled() = prefStore.getBoolean(DebuggerPreferencePage.EXP_EVAL_ENABLE_CODE_COMPLETION)
    }
    // there's known issue related to internal implementation of ContentProposalAdapter
    // (handling SWT.BS (backspace) in handleEvents method in private, final class TargetControlListener)
    // when we have only one letter in input field, shown proposals filtered by this letter and we'll delete it using backspace,
    // proposals are not refreshed (nothing happens because there's a check in code whether input field's content is not empty)
    val proposal = new ContentProposalAdapter(inputCommandField, new StyledTextContentAdapter, provider, KeyStroke.getInstance("Ctrl+Space"), null)
    proposal.setFilterStyle(ContentProposalAdapter.FILTER_NONE)
    proposal.setPopupSize(new Point(codeCompletionPopupWidth, codeCompletionPopupHeight))

    inputCommandField.addVerifyKeyListener(new VerifyKeyListener { // prevent insertion of \n after pressing Enter when dialog with proposals is shown
      override def verifyKey(event: VerifyEvent) {
        if (proposal.isProposalPopupOpen() && event.keyCode == SWT.CR)
          event.doit = false
      }
    })

    // enable evaluate button only when there's expression to evaluate
    inputCommandField.addModifyListener(new ModifyListener() {
      override def modifyText(event: ModifyEvent) {
        evaluateAction.setEnabled(!inputCommandField.isEmpty && !inputCommandField.isHelpTextDisplayed)
      }
    })
  }

  override def setFocus() {}

  private object evaluateAction extends Action("Evaluate") {
    setToolTipText("Evaluate")

    setImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_ELCL_RESUME))
    setDisabledImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_DLCL_RESUME))
    setHoverImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_ELCL_RESUME))
    setEnabled(false)

    override def run() {
      forceEvaluationAndAddToHistory()
    }
  }

  private object clearOutputAction extends Action("Clear Output") {
    setToolTipText("Clear Output")
    setImageDescriptor(ConsolePluginImages.getImageDescriptor(IInternalConsoleConstants.IMG_ELCL_CLEAR));
    setDisabledImageDescriptor(ConsolePluginImages.getImageDescriptor(IInternalConsoleConstants.IMG_DLCL_CLEAR));
    setHoverImageDescriptor(ConsolePluginImages.getImageDescriptor(IConsoleConstants.IMG_LCL_CLEAR));

    override def run() {
      resultsTextWidget.setText("")
    }
  }

  private object clearConsoleAndHistoryAction extends Action("Clear Output And History") {
    setToolTipText("Clear Output And History")
    setImageDescriptor(ConsolePluginImages.getImageDescriptor(IInternalConsoleConstants.IMG_ELCL_CLEAR));
    setDisabledImageDescriptor(ConsolePluginImages.getImageDescriptor(IInternalConsoleConstants.IMG_DLCL_CLEAR));
    setHoverImageDescriptor(ConsolePluginImages.getImageDescriptor(IConsoleConstants.IMG_LCL_CLEAR));

    override def run() {
      resultsTextWidget.setText("")
      inputCommandField.clearHistory()
    }
  }

  private def forceEvaluationAndAddToHistory() = inputCommandField.forceEvaluation()
}

object ExpressionEvaluatorConsoleView {
  def evaluate(project: IProject, page: IWorkbenchPage, expression: String) {
    val evaluatorView = show(IWorkbenchPage.VIEW_VISIBLE, page)
    evaluatorView.inputCommandField.setText(expression)
    evaluatorView.forceEvaluationAndAddToHistory()
  }

  def show(mode: Int, page: IWorkbenchPage): ExpressionEvaluatorConsoleView = {
    val viewPart = page.showView("org.scalaide.debug.internal.ui.ExpressionEvaluation", null, mode)
    viewPart.asInstanceOf[ExpressionEvaluatorConsoleView]
  }
}
