package scala.tools.eclipse
package interpreter

import org.eclipse.jface.action.Separator
import org.eclipse.jface.action.Action
import org.eclipse.jdt.ui.PreferenceConstants
import org.eclipse.jface.resource.JFaceResources
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.custom.StyleRange
import org.eclipse.swt.SWT
import org.eclipse.ui.IWorkbenchPartSite
import org.eclipse.swt.widgets.Composite
import org.eclipse.ui.IPropertyListener
import org.eclipse.ui.part.ViewPart
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.custom.StyledText
import org.eclipse.swt.widgets.{Label, Caret}
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import scala.tools.eclipse.ui.CommandField

// for the toolbar images
import org.eclipse.debug.internal.ui.IInternalDebugUIConstants
import org.eclipse.debug.internal.ui.DebugPluginImages
import org.eclipse.ui.internal.console.IInternalConsoleConstants
import org.eclipse.ui.console.IConsoleConstants
import org.eclipse.ui.internal.console.ConsolePluginImages
import org.eclipse.jface.action.IAction

class ReplConsoleView extends ViewPart {

  private class ReplEvaluator extends scala.tools.eclipse.ui.CommandField.Evaluator {
    override def eval(command: String) {
      val repl = EclipseRepl.replForProject(scalaProject)
      assert(repl.isDefined, "A REPL should always exist at this point")
      repl.get.interpret(code = command, withReplay = false)
    }
  }
  
  private var textWidget: StyledText = null
  private var codeBgColor: Color = null
  private var codeFgColor: Color = null
  private var errorFgColor: Color = null

  private var projectName: String = ""
  private var scalaProject: ScalaProject = null
  private var isStopped = true
  private var inputField: CommandField = null
   
  def setScalaProject(project: ScalaProject) {
    scalaProject = project
    
    if (isStopped) {
      clearConsoleAction.run
      setStarted
    }
  }
    
  private object stopReplAction extends Action("Terminate") {
    setToolTipText("Terminate") 
    
    import IInternalDebugUIConstants._
    setImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_LCL_TERMINATE))
    setDisabledImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_DLCL_TERMINATE))
    setHoverImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_LCL_TERMINATE))
    
    override def run() {
      EclipseRepl.stopRepl(scalaProject)
      setStopped
    }
  }
    
  private object clearConsoleAction extends Action("Clear Output") {
    setToolTipText("Clear Output")
    setImageDescriptor(ConsolePluginImages.getImageDescriptor(IInternalConsoleConstants.IMG_ELCL_CLEAR));
    setDisabledImageDescriptor(ConsolePluginImages.getImageDescriptor(IInternalConsoleConstants.IMG_DLCL_CLEAR));
    setHoverImageDescriptor(ConsolePluginImages.getImageDescriptor(IConsoleConstants.IMG_LCL_CLEAR));   
    
    override def run() {
      textWidget.setText("")
      setEnabled(false)
    }
  }
  
  private object relaunchAction extends Action("Relaunch Interpreter") {
    setToolTipText("Terminate and Replay")
    
    import IInternalDebugUIConstants._    
    setImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_ELCL_TERMINATE_AND_RELAUNCH))
    setDisabledImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_DLCL_TERMINATE_AND_RELAUNCH))
    setHoverImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_ELCL_TERMINATE_AND_RELAUNCH))
    
    override def run() {
      clearConsoleAction.run
      EclipseRepl.relaunchRepl(scalaProject)
    }  
  }
  
  object replayAction extends Action("Replay Interpreter History") {
    setToolTipText("Replay All Commands")
    
    import IInternalDebugUIConstants._    
    setImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_ELCL_RESTART))
    setDisabledImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_DLCL_RESTART))
    setHoverImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_ELCL_RESTART))
    
    setEnabled(false)
    
    override def run() {
      // TODO: relaunch the interpreter if the repl is terminated
      // problem: when the interpreter is stopped, history will be lost
      EclipseRepl.replayRepl(scalaProject)
    }
  }  
  
  object refreshOnRebuildAction extends Action("Replay History on Project Rebuild", IAction.AS_CHECK_BOX) with BuildSuccessListener {
    setToolTipText("Replay History on Project Rebuild")
    
    setImageDescriptor(ScalaImages.REFRESH_REPL_TOOLBAR)
    setHoverImageDescriptor(ScalaImages.REFRESH_REPL_TOOLBAR)
    
    override def run() {
      if (isChecked) scalaProject addBuildSuccessListener this
      else scalaProject removeBuildSuccessListener this
    }
    
    def buildSuccessful() {
      if (!isStopped) {
        util.SWTUtils asyncExec {
          displayOutput("\n------ Project Rebuilt, Replaying Interpreter Transcript ------\n")
          EclipseRepl.relaunchRepl(scalaProject)
        }
      }
    }
  }
  
  private def setStarted {
    isStopped = false

    stopReplAction.setEnabled(true)
    relaunchAction.setEnabled(true)
    replayAction.setEnabled(true)
    
    inputField.setEnabled(true)

    setContentDescription("Scala Interpreter (Project: " + projectName + ")")
  }

  private def setStopped {
    isStopped = true

    stopReplAction.setEnabled(false)
    relaunchAction.setEnabled(false)
    replayAction.setEnabled(false)
    
    inputField.setEnabled(false)
    inputField.clear()
    
    setContentDescription("<terminated> " + getContentDescription)
  }
    
  override def createPartControl(parent: Composite) {
    projectName = getViewSite.getSecondaryId
    if (projectName == null) projectName = ""
    
    codeBgColor = new Color(parent.getDisplay, 230, 230, 230)   // light gray
    codeFgColor = new Color(parent.getDisplay, 64, 0, 128)      // eggplant
    errorFgColor = new Color(parent.getDisplay, 128, 0, 64)     // maroon
    
    val panel = new Composite(parent, SWT.NONE)
    panel.setLayout(new GridLayout(2, false)) //two columns grid
     
    // 1st row
    textWidget = new StyledText(panel, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL)
    textWidget.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1)) // span two columns
    textWidget.setEditable(false)
    textWidget.setCaret(new Caret(textWidget, SWT.NONE))
    
    
    val editorFont = JFaceResources.getFont(PreferenceConstants.EDITOR_TEXT_FONT)    
    textWidget.setFont(editorFont) // java editor font
    
    // 2nd row
    val inputLabel = new Label(panel, SWT.NULL)
    inputLabel.setText("Evaluate:")
    
    inputField = new CommandField(panel, SWT.BORDER | SWT.SINGLE) {
      override protected def helpText = "<type an expression>" 
      setEvaluator(new ReplEvaluator)
    }
    inputField.setFont(editorFont)
    inputField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL))
     
    val toolbarManager = getViewSite.getActionBars.getToolBarManager
    toolbarManager.add(replayAction)
    toolbarManager.add(new Separator)
    toolbarManager.add(stopReplAction)
    toolbarManager.add(relaunchAction)
    toolbarManager.add(new Separator)
    toolbarManager.add(clearConsoleAction)
    toolbarManager.add(new Separator)
    toolbarManager.add(refreshOnRebuildAction)
    
    setPartName("Scala Interpreter (" + projectName + ")")
    setStarted
  }

  override def setFocus() { }
       
  /**
   * Display the string with code formatting
   */
  private[interpreter] def displayCode(text: String) {
    if (textWidget.getCharCount != 0) // don't insert a newline if this is the first line of code to be displayed
      displayOutput("\n")
    appendText(text, codeFgColor, codeBgColor, SWT.ITALIC, insertNewline = true)
    displayOutput("\n")
  }

  private[interpreter] def displayOutput(text: String) {
    appendText(text, null, null, SWT.NORMAL)
  }
  
  def displayError(text: String) {
    appendText(text, errorFgColor, null, SWT.NORMAL)
  }
  
  private def appendText(text: String, fgColor: Color, bgColor: Color, fontStyle: Int, insertNewline: Boolean = false) {
    val lastOffset = textWidget.getCharCount
    val oldLastLine = textWidget.getLineCount
    
    val outputStr = 
      if (insertNewline) "\n" + text.stripLineEnd + "\n\n"
      else text

    textWidget.append(outputStr)        
    textWidget.setStyleRange(new StyleRange(lastOffset, outputStr.length, fgColor, null, fontStyle))
    
    val lastLine = textWidget.getLineCount
    if (bgColor != null)
      textWidget.setLineBackground(oldLastLine - 1, lastLine - oldLastLine, bgColor)
    textWidget.setTopIndex(textWidget.getLineCount - 1)  
    
    clearConsoleAction.setEnabled(true)
  }
  
  override def dispose() {
    codeBgColor.dispose
    codeFgColor.dispose
    errorFgColor.dispose
    
    if (!isStopped)
      EclipseRepl.stopRepl(scalaProject, flush = false)
      
    scalaProject removeBuildSuccessListener refreshOnRebuildAction
  }
}