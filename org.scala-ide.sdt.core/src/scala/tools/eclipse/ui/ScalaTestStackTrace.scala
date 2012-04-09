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

class ScalaTestStackTrace(parent: Composite, fTestRunner: ScalaTestRunnerViewPart, toolBar: ToolBar) {
  
  private val MAX_LABEL_LENGTH = 256
  
  private val fTable = new Table(parent, SWT.SINGLE | SWT.V_SCROLL | SWT.H_SCROLL)
  private val fFailureTableDisplay = new FailureTableDisplay(fTable)
  private var fInputTrace: String = null
  var node: Node = null
  
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
        val st = node.getStackTraces
        node.getStackTraces match {
          case Some(stackTraces) => 
            val stackTraceElement = stackTraces(selectedIdx)
            val model = JavaCore.create(ResourcesPlugin.getWorkspace.getRoot)
            val javaProject = model.getJavaProject(fTestRunner.fTestRunSession.projectName)
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
    fInputTrace = null
  }
  
  def getComposite: Composite = fTable
  
  def showFailure(selectedNode: Option[Node]) {
    val trace = 
      selectedNode match {
        case Some(selectedNode) => 
          node = selectedNode
          selectedNode.getStackTraces match {
            case Some(stackTraces) => 
              stackTraces.mkString("\n")
            case None => 
              ""
          }
        case None =>
          ""
      }
    updateTable(trace)
  }
  
  private def updateTable(rawTrace: String) {
    if (rawTrace == null || rawTrace.trim == "")
      clear()
    else {
      val trace = rawTrace.trim
      fTable.setRedraw(false)
      fTable.removeAll()
      new TextualTrace(trace, getFilterPatterns)
            .display(fFailureTableDisplay, MAX_LABEL_LENGTH)
      fTable.setRedraw(true)
    }
  }
  
  private def getFilterPatterns: Array[String] = {
    // TODO: JUnit get it from preference pages, we don't have it so will just return empty array.
    Array.empty[String]
  }
}