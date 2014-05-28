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
import org.eclipse.jface.action.IAction.AS_RADIO_BUTTON
import org.eclipse.jface.action.IMenuListener
import org.eclipse.jface.action.IMenuManager
import org.eclipse.jface.action.MenuManager
import org.eclipse.jface.action.Separator
import org.eclipse.jface.bindings.keys.KeyStroke
import org.eclipse.jface.fieldassist.ContentProposalAdapter
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.SashForm
import org.eclipse.swt.custom.VerifyKeyListener
import org.eclipse.swt.events.ModifyEvent
import org.eclipse.swt.events.ModifyListener
import org.eclipse.swt.events.VerifyEvent
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.layout.FillLayout
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
class ExpressionEvaluatorView extends ViewPart with InterpreterConsoleView {
  // TODO it would be nice to add support for undo and redo (ctrl+z / ctrl+y)

  import ExpressionEvaluatorView.ExpressionEvaluatorViewLayoutAction
  import ExpressionEvaluatorViewLayoutType.ONLY_CONSOLE
  import ExpressionEvaluatorViewLayoutType.CONSOLE_AND_TREE_HORIZONTALLY
  import ExpressionEvaluatorViewLayoutType.CONSOLE_AND_TREE_VERTICALLY
  import ExpressionEvaluatorViewLayoutType.INPUT_AND_TREE_HORIZONTALLY
  import ExpressionEvaluatorViewLayoutType.INPUT_AND_TREE_VERTICALLY

  protected var evaluatorPanel: SashForm = null
  protected var currentLayoutType: ExpressionEvaluatorViewLayoutType.Value = null
  protected var treeView: ExpressionResultTreeView = null

  protected val codeCompletionPopupWidth = 512
  protected val codeCompletionPopupHeight = 320
  protected val preferenceStore = ScalaDebugPlugin.plugin.getPreferenceStore()

  protected val resultCallback = (resultObjRef: com.sun.jdi.ObjectReference, outputText: String) => {
    if (shouldShowConsoleOutput(currentLayoutType)) displayOutput(outputText)
    treeView.reloadWithResult(resultObjRef, Some(outputText))
  }

  protected val errorCallback = (text: String) => {
    if (shouldShowConsoleOutput(currentLayoutType)) displayError(text)
    treeView.reloadWithErrorText(text)
  }

  override def evaluate(text: String): Unit = {
    if (shouldShowConsoleOutput(currentLayoutType)) displayCode(text)
    ExpressionManager.compute(text, resultCallback, errorCallback)
  }

  override def setFocus(): Unit = {}

  override def createPartControl(parent: Composite): Unit = {
    setPartName("Scala Expression Evaluator")

    evaluatorPanel = new SashForm(parent, SWT.VERTICAL)
    evaluatorPanel.setLayout(new FillLayout)
    createInterpreterPartControl(evaluatorPanel)
    createTreeView(evaluatorPanel)
    initActionBar()

    val defaultLayout = ExpressionEvaluatorViewLayoutType(preferenceStore.getString(DebuggerPreferencePage.EXP_EVAL_DEFAULT_LAYOUT).toInt)
    updateLayout(defaultLayout)
  }

  override def createInterpreterPartControl(parent: Composite): Unit = {
    super.createInterpreterPartControl(parent)

    inputCommandField.setLineNumbersVisibility(preferenceStore.getBoolean(DebuggerPreferencePage.EXP_EVAL_SHOW_LINE_NUMBERS_BY_DEFAULT))

    val proposalProvider = new SimpleContentProposalProvider {
      override protected def isCodeCompletionEnabled() = preferenceStore.getBoolean(DebuggerPreferencePage.EXP_EVAL_ENABLE_CODE_COMPLETION)
    }

    // there's known issue related to internal implementation of ContentProposalAdapter
    // (handling SWT.BS (backspace) in handleEvents method in private, final class TargetControlListener)
    // when we have only one letter in input field, shown proposals filtered by this letter and we'll delete it using backspace,
    // proposals are not refreshed (nothing happens because there's a check in code whether input field's content is not empty)
    val proposalAdapter = new ContentProposalAdapter(inputCommandField, new StyledTextContentAdapter, proposalProvider, KeyStroke.getInstance("Ctrl+Space"), null)
    proposalAdapter.setFilterStyle(ContentProposalAdapter.FILTER_NONE)
    proposalAdapter.setPopupSize(new Point(codeCompletionPopupWidth, codeCompletionPopupHeight))

    inputCommandField.addVerifyKeyListener(new VerifyKeyListener { // prevent insertion of \n after pressing Enter when dialog with proposals is shown
      override def verifyKey(event: VerifyEvent) {
        if (proposalAdapter.isProposalPopupOpen() && event.keyCode == SWT.CR)
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

  protected def createTreeView(parent: Composite): Unit = {
    treeView = new ExpressionResultTreeView(parent)
  }

  protected def initActionBar(): Unit = {
    addActionsToActionBar()
    addLayoutChoiceToActionBarMenu()
  }

  protected def updateLayout(layoutType: ExpressionEvaluatorViewLayoutType.Value): Unit = {
    currentLayoutType = layoutType
    val consoleVisible = shouldShowConsoleOutput(layoutType)
    resultsTextWidget.setVisible(consoleVisible)
    treeView.setVisible(shouldShowTree(layoutType))
    updateTreePosition(layoutType)
    refreshLayout()

    // don't clear in the case when there are only input and tree - in this case expression used to get shown tree isn't shown in other place
    inputCommandField.clearTextAfterEvaluation = consoleVisible
  }

  protected def refreshLayout(): Unit = {
    interpreterPanel.layout()
    evaluatorPanel.layout()
  }

  private def updateTreePosition(layoutType: ExpressionEvaluatorViewLayoutType.Value): Unit = layoutType match {
    case CONSOLE_AND_TREE_HORIZONTALLY | INPUT_AND_TREE_HORIZONTALLY => evaluatorPanel.setOrientation(SWT.HORIZONTAL)
    case _ => evaluatorPanel.setOrientation(SWT.VERTICAL)
  }

  private def shouldShowConsoleOutput(layoutType: ExpressionEvaluatorViewLayoutType.Value) =
    Seq(ONLY_CONSOLE, CONSOLE_AND_TREE_HORIZONTALLY, CONSOLE_AND_TREE_VERTICALLY).contains(layoutType)

  private def shouldShowTree(layoutType: ExpressionEvaluatorViewLayoutType.Value) =
    layoutType != ONLY_CONSOLE

  protected def setInputText(text: String): Unit = {
    inputCommandField.hideHelpText()
    inputCommandField.setText(text)
    inputCommandField.maybeShowHelpText()
  }

  protected def executeEvaluationAndAddToHistory(): Unit = inputCommandField.executeEvaluation()

  private def addActionsToActionBar(): Unit = {
    val toolbarManager = getViewSite.getActionBars.getToolBarManager()
    toolbarManager.add(evaluateAction)
    toolbarManager.add(clearOutputAction)
    toolbarManager.add(new Separator)
    toolbarManager.add(clearConsoleAndHistoryAction)
  }

  private def addLayoutChoiceToActionBarMenu(): Unit = {
    val actionBarMenuManager = getViewSite().getActionBars().getMenuManager()

    val layoutChoiceActions = Seq(
      consoleViewLayoutAction,
      consoleWithTreeViewHorizontalLayoutAction,
      consoleWithTreeViewVerticalLayoutAction,
      treeViewHorizontalLayoutAction,
      treeViewVerticalLayoutAction)

    val layoutSubMenu = new MenuManager("Layout")
    actionBarMenuManager.add(layoutSubMenu)
    layoutChoiceActions.foreach(layoutSubMenu.add)

    layoutSubMenu.addMenuListener(new IMenuListener() {
      override def menuAboutToShow(manager: IMenuManager): Unit =
        layoutChoiceActions.foreach(action => action.setChecked(action.layoutType == currentLayoutType))
    })
  }

  /*--------------- actions visible on actions bar ---------------*/

  private object evaluateAction extends Action("Evaluate") {
    setToolTipText("Evaluate")

    setImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_ELCL_RESUME))
    setDisabledImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_DLCL_RESUME))
    setHoverImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_ELCL_RESUME))
    setEnabled(false)

    override def run(): Unit = executeEvaluationAndAddToHistory()
  }

  private object clearOutputAction extends Action("Clear Output") {
    setToolTipText("Clear Output")
    setImageDescriptor(ConsolePluginImages.getImageDescriptor(IInternalConsoleConstants.IMG_ELCL_CLEAR))
    setDisabledImageDescriptor(ConsolePluginImages.getImageDescriptor(IInternalConsoleConstants.IMG_DLCL_CLEAR))
    setHoverImageDescriptor(ConsolePluginImages.getImageDescriptor(IConsoleConstants.IMG_LCL_CLEAR))

    override def run(): Unit = {
      resultsTextWidget.clear()
      treeView.clear()
    }
  }

  private object clearConsoleAndHistoryAction extends Action("Clear Output And History") {
    setToolTipText("Clear Output And History")
    setImageDescriptor(ConsolePluginImages.getImageDescriptor(IInternalConsoleConstants.IMG_ELCL_CLEAR))
    setDisabledImageDescriptor(ConsolePluginImages.getImageDescriptor(IInternalConsoleConstants.IMG_DLCL_CLEAR))
    setHoverImageDescriptor(ConsolePluginImages.getImageDescriptor(IConsoleConstants.IMG_LCL_CLEAR))

    override def run(): Unit = {
      clearOutputAction.run()
      inputCommandField.clearHistory()
    }
  }

  /*--------------- actions related to layout available via actions bar menu ---------------*/

  private object consoleViewLayoutAction extends ExpressionEvaluatorViewLayoutAction(ONLY_CONSOLE, updateLayout) {
    setImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_ELCL_DETAIL_PANE_HIDE))
  }

  private object consoleWithTreeViewHorizontalLayoutAction extends ExpressionEvaluatorViewLayoutAction(CONSOLE_AND_TREE_HORIZONTALLY, updateLayout) {
    setImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_ELCL_DETAIL_PANE_RIGHT))
  }

  private object consoleWithTreeViewVerticalLayoutAction extends ExpressionEvaluatorViewLayoutAction(CONSOLE_AND_TREE_VERTICALLY, updateLayout) {
    setImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_ELCL_DETAIL_PANE_UNDER))
  }

  private object treeViewHorizontalLayoutAction extends ExpressionEvaluatorViewLayoutAction(INPUT_AND_TREE_HORIZONTALLY, updateLayout) {
    setImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_ELCL_DETAIL_PANE_RIGHT))
  }

  private object treeViewVerticalLayoutAction extends ExpressionEvaluatorViewLayoutAction(INPUT_AND_TREE_VERTICALLY, updateLayout) {
    setImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_ELCL_DETAIL_PANE_UNDER))
  }
}

object ExpressionEvaluatorView {
  def evaluate(project: IProject, page: IWorkbenchPage, expression: String): Unit = {
    val evaluatorView = show(IWorkbenchPage.VIEW_VISIBLE, page)
    evaluatorView.setInputText(expression)
    evaluatorView.executeEvaluationAndAddToHistory()
  }

  def show(mode: Int, page: IWorkbenchPage): ExpressionEvaluatorView = {
    val viewPart = page.showView("org.scalaide.debug.internal.ui.ExpressionEvaluation", null, mode)
    viewPart.asInstanceOf[ExpressionEvaluatorView]
  }

  private[ExpressionEvaluatorView] abstract class ExpressionEvaluatorViewLayoutAction(val layoutType: ExpressionEvaluatorViewLayoutType.Value,
    updateLayoutFun: ExpressionEvaluatorViewLayoutType.Value => Unit)
    extends Action(layoutType.toString(), AS_RADIO_BUTTON) {
    setToolTipText(layoutType.toString())

    override def run(): Unit = updateLayoutFun(layoutType)
  }
}

object ExpressionEvaluatorViewLayoutType extends Enumeration {
  val ONLY_CONSOLE = Value(1, "Console view")
  val CONSOLE_AND_TREE_HORIZONTALLY = Value(2, "Console view and tree at the right")
  val CONSOLE_AND_TREE_VERTICALLY = Value(3, "Console view and tree at the bottom")
  val INPUT_AND_TREE_HORIZONTALLY = Value(4, "Input and tree at the right")
  val INPUT_AND_TREE_VERTICALLY = Value(5, "Input and tree at the bottom")
}
