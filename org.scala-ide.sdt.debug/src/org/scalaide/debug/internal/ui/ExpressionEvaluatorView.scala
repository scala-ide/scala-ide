/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.ui

import org.eclipse.core.resources.IProject
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.debug.internal.ui.DebugPluginImages
import org.eclipse.debug.internal.ui.IInternalDebugUIConstants
import org.eclipse.jface.action.Action
import org.eclipse.jface.action.IAction.AS_CHECK_BOX
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
import org.eclipse.swt.events.VerifyEvent
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.widgets.Composite
import org.eclipse.ui.IWorkbenchPage
import org.eclipse.ui.console.IConsoleConstants
import org.eclipse.ui.internal.console.ConsolePluginImages
import org.eclipse.ui.internal.console.IInternalConsoleConstants
import org.eclipse.ui.part.ViewPart
import org.scalaide.debug.internal.expression.EvaluationFailure
import org.scalaide.debug.internal.expression.ExpressionEvaluatorResult
import org.scalaide.debug.internal.expression.ExpressionManager
import org.scalaide.debug.internal.expression.ProgressMonitor
import org.scalaide.debug.internal.expression.SuccessWithValue
import org.scalaide.debug.internal.expression.SuccessWithoutValue
import org.scalaide.debug.internal.preferences.ExpressionEvaluatorPreferences
import org.scalaide.debug.internal.ui.completion.SimpleContentProposalProvider
import org.scalaide.ui.internal.repl.InterpreterConsoleView
import org.scalaide.util.eclipse.SWTUtils
import org.scalaide.util.ui.DisplayThread

/**
 * UI component for debug expression evaluation.
 */
class ExpressionEvaluatorView extends ViewPart with InterpreterConsoleView {
  // TODO - O-5696 - it would be nice to add support for undo and redo (ctrl+z / ctrl+y)

  import ExpressionEvaluatorView.ExpressionEvaluatorViewLayoutAction
  import ExpressionEvaluatorViewLayoutType.ONLY_CONSOLE
  import ExpressionEvaluatorViewLayoutType.CONSOLE_AND_TREE_HORIZONTALLY
  import ExpressionEvaluatorViewLayoutType.CONSOLE_AND_TREE_VERTICALLY
  import ExpressionEvaluatorViewLayoutType.INPUT_AND_TREE_HORIZONTALLY
  import ExpressionEvaluatorViewLayoutType.INPUT_AND_TREE_VERTICALLY
  import SWTUtils.noArgFnToModifyListener

  protected var currentLayoutType = ExpressionEvaluatorPreferences.layoutType
  protected var evaluatorPanel: SashForm = null
  protected var treeView: ExpressionResultTreeView = null

  protected val codeCompletionPopupWidth = 512
  protected val codeCompletionPopupHeight = 320

  override def evaluate(text: String): Unit = {
    if (shouldShowConsoleOutput(currentLayoutType)) displayCode(text)

    def refreshUI(result: ExpressionEvaluatorResult): Unit = DisplayThread.asyncExec {
      result match {
        case SuccessWithValue(scalaValue, outputText) =>
          if (shouldShowConsoleOutput(currentLayoutType)) displayOutput(outputText)
          treeView.reloadWithResult(scalaValue, outputText)
        case SuccessWithoutValue(outputText) =>
          if (shouldShowConsoleOutput(currentLayoutType)) displayOutput(outputText)
          treeView.reloadWithErrorText(s"$outputText\nTree for this result is not available")
        case EvaluationFailure(errorMessage) =>
          if (shouldShowConsoleOutput(currentLayoutType)) displayError(errorMessage)
          treeView.reloadWithErrorText(errorMessage)
      }
    }

    new Job(s"Evaluating: '$text'") {
      override protected def run(monitor: IProgressMonitor): IStatus = {
        monitor.beginTask(s"Evaluating: '$text'", ExpressionManager.numberOfPhases + 2) // 1 for running
        val result = ExpressionManager.compute(text, new ProgressMonitor {
          override def reportProgress(amount: Int): Unit = monitor.worked(amount)
          override def startNamedSubTask(name: String): Unit = monitor.setTaskName(name)
        })
        refreshUI(result)
        monitor.done()
        Status.OK_STATUS
      }
    }.schedule()
  }

  override def setFocus(): Unit = {}

  override def createPartControl(parent: Composite): Unit = {
    setPartName("Scala Expression Evaluator")

    evaluatorPanel = new SashForm(parent, SWT.VERTICAL)
    evaluatorPanel.setLayout(new FillLayout)
    createInterpreterPartControl(evaluatorPanel)
    createTreeView(evaluatorPanel)
    initActionBar()

    updateLayout(currentLayoutType)
  }

  override def createInterpreterPartControl(parent: Composite): Unit = {
    super.createInterpreterPartControl(parent)

    inputCommandField.setLineNumbersVisibility(ExpressionEvaluatorPreferences.showLineNumbers)

    val proposalProvider = new SimpleContentProposalProvider {
      override protected def isCodeCompletionEnabled() = ExpressionEvaluatorPreferences.isCodeCompletionEnabled
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
    inputCommandField.addModifyListener { () =>
      evaluateAction.setEnabled(!inputCommandField.isEmpty && !inputCommandField.isHelpTextDisplayed)
    }
  }

  override def dispose(): Unit = {
    evaluatorPanel.dispose()
    treeView.dispose()
    super.dispose()
  }

  protected def createTreeView(parent: Composite): Unit = {
    treeView = new ExpressionResultTreeView(parent)
  }

  protected def initActionBar(): Unit = {
    addActionsToActionBar()
    addLayoutChoiceToActionBarMenu()
  }

  protected def updateLayout(layoutType: ExpressionEvaluatorViewLayoutType.Value): Unit = {
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

  protected override def doOnLineNumbersVisibilityUpdate(enabled: Boolean): Unit = {
    ExpressionEvaluatorPreferences.showLineNumbers = enabled
  }

  protected def updateAndRememberLayout(layoutType: ExpressionEvaluatorViewLayoutType.Value): Unit = {
    currentLayoutType = layoutType
    updateLayout(layoutType)
    ExpressionEvaluatorPreferences.layoutType = layoutType
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
    toolbarManager.add(toggleShowLogicalStructureAction)
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

  private object toggleShowLogicalStructureAction extends Action("Show Logical Structure In Tree", AS_CHECK_BOX) {

    setToolTipText("Show Logical Structure In Tree")
    setImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_ELCL_SHOW_LOGICAL_STRUCTURE))
    setDisabledImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_DLCL_SHOW_LOGICAL_STRUCTURE))
    setHoverImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_LCL_SHOW_LOGICAL_STRUCTURE))
    setChecked(ExpressionEvaluatorPreferences.showCollectionsLogicalStructure)

    override def run(): Unit = {
      ExpressionEvaluatorPreferences.showCollectionsLogicalStructure = !ExpressionEvaluatorPreferences.showCollectionsLogicalStructure
      treeView.refresh()
    }
  }

  /*--------------- actions related to layout available via actions bar menu ---------------*/

  private object consoleViewLayoutAction extends ExpressionEvaluatorViewLayoutAction(ONLY_CONSOLE, updateAndRememberLayout) {
    setImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_ELCL_DETAIL_PANE_HIDE))
  }

  private object consoleWithTreeViewHorizontalLayoutAction extends ExpressionEvaluatorViewLayoutAction(CONSOLE_AND_TREE_HORIZONTALLY, updateAndRememberLayout) {
    setImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_ELCL_DETAIL_PANE_RIGHT))
  }

  private object consoleWithTreeViewVerticalLayoutAction extends ExpressionEvaluatorViewLayoutAction(CONSOLE_AND_TREE_VERTICALLY, updateAndRememberLayout) {
    setImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_ELCL_DETAIL_PANE_UNDER))
  }

  private object treeViewHorizontalLayoutAction extends ExpressionEvaluatorViewLayoutAction(INPUT_AND_TREE_HORIZONTALLY, updateAndRememberLayout) {
    setImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_ELCL_DETAIL_PANE_RIGHT))
  }

  private object treeViewVerticalLayoutAction extends ExpressionEvaluatorViewLayoutAction(INPUT_AND_TREE_VERTICALLY, updateAndRememberLayout) {
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
