package scala.tools.eclipse.ui

import org.eclipse.swt.widgets.Table
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.SWT
import org.eclipse.jdt.internal.junit.ui.FailureTableDisplay
import org.eclipse.jface.util.OpenStrategy
import org.eclipse.jface.util.IOpenEventListener
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.widgets.ToolBar
import org.eclipse.jdt.internal.junit.ui.TextualTrace
import org.eclipse.jdt.core.JavaCore
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.jface.dialogs.MessageDialog
import scala.tools.eclipse.ScalaProject
import org.eclipse.jface.action.ToolBarManager
import org.eclipse.jface.action.Action
import scala.tools.eclipse.ScalaImages

class ScalaTestStackTrace(parent: Composite, fTestRunner: ScalaTestRunnerViewPart, toolBar: ToolBar) {
  
  private val MAX_LABEL_LENGTH = 256
  
  private val fTable = new Table(parent, SWT.SINGLE | SWT.V_SCROLL | SWT.H_SCROLL)
  private val fFailureTableDisplay = new FailureTableDisplay(fTable)
  private var fStackDepth: Option[Int] = None
  private var fStackTraces: Option[Array[StackTraceElement]] = None
  var node: Node = null
  
  private val toolBarManager = new ToolBarManager(toolBar)
  private val enableStackFoldingAction = new EnableStackFoldingAction(this)
  
  toolBarManager.add(enableStackFoldingAction)
  toolBarManager.update(true)
  
  
  private val handler = new OpenStrategy(fTable)
  handler.addOpenListener(new IOpenEventListener() {
    
    private def getShell = fTestRunner.getSite.getShell
    
    private def notifyLocationNotFound() {
      MessageDialog.openError(getShell, "Cannot Open Editor", 
                              "Cannot open source location of the selected element")
    }
    
    def handleOpen(e: SelectionEvent) {
      val selectedIdx = fTable.getSelectionIndex
      if (selectedIdx >= 0) {
        fStackTraces match {
          case Some(stackTraces) => 
            val foldedStackTraces = getFoldedStackTraces(stackTraces)
            val stackTraceElement = foldedStackTraces(selectedIdx)
            val model = JavaCore.create(ResourcesPlugin.getWorkspace.getRoot)
            val javaProject = model.getJavaProject(fTestRunner.getSession.projectName)
            if (javaProject != null) {
              val openAction = new GoToSourceAction(node, fTestRunner)
              openAction.openSourceFileLineNumber(ScalaProject(javaProject.getProject), stackTraceElement.fileName, stackTraceElement.lineNumber)
            }
            else
              notifyLocationNotFound()
          case None => 
            notifyLocationNotFound()
        }
      }
      else
        notifyLocationNotFound()
    }
  })
  
  /*ToolBarManager failureToolBarmanager= new ToolBarManager(toolBar);
  failureToolBarmanager.add(new EnableStackFilterAction(this));
  fCompareAction = new CompareResultsAction(this);
  fCompareAction.setEnabled(false);
  failureToolBarmanager.add(fCompareAction);
  failureToolBarmanager.update(true);*/
  
  def clear() {
    fTable.removeAll()
    fStackDepth = None
    fStackTraces = None
  }
  
  def getComposite: Composite = fTable
  
  def showFailure(selectedNode: Option[Node]) {
    fStackTraces = 
      selectedNode match {
        case Some(selectedNode) => 
          node = selectedNode
          fStackDepth = selectedNode.getStackDepth
          selectedNode.getStackTraces
        case None =>
          None
      }
    updateTable(fStackTraces, fStackDepth)
  }
  
  private def getFoldedStackTraces(stackTraces: Array[StackTraceElement]) = 
    if (enableStackFoldingAction.isChecked) {
      fStackDepth match {
        case Some(stackDepth) =>
          stackTraces.drop(stackDepth)
        case None =>
          stackTraces
      }
    }
    else
      stackTraces
  
  private def updateTable(stackTraces: Option[Array[StackTraceElement]], stackDepth: Option[Int]) {
    stackTraces match {
      case Some(stackTraces) => 
        val foldedStackTraces = getFoldedStackTraces(stackTraces)
        val trace = foldedStackTraces.mkString("\n").trim
        fTable.setRedraw(false)
        fTable.removeAll()
        new TextualTrace(trace, getFilterPatterns)
            .display(fFailureTableDisplay, MAX_LABEL_LENGTH)
        fTable.setRedraw(true)
      case None => 
        clear()
    }
  }
  
  def refresh() {
    updateTable(fStackTraces, fStackDepth)
  }
  
  private def getFilterPatterns: Array[String] = {
    // TODO: JUnit get it from preference pages, we don't have it so will just return empty array.
    Array.empty[String]
  }
  
  private class EnableStackFoldingAction(fView: ScalaTestStackTrace) extends Action("Stack Folding") {
    setDescription("Fold the Stack Trace")
    setToolTipText("Fold Stack Trace")
    setDisabledImageDescriptor(ScalaImages.SCALATEST_STACK_FOLD_DISABLED)
    setHoverImageDescriptor(ScalaImages.SCALATEST_STACK_FOLD_ENABLED)
    setImageDescriptor(ScalaImages.SCALATEST_STACK_FOLD_ENABLED)
    setChecked(true)
    
    override def run() {
      fView.refresh()
    }
  }
}