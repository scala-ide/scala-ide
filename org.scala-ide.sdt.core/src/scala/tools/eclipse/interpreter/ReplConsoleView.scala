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
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import scala.tools.eclipse.ui.CommandField

// for the toolbar images
import org.eclipse.debug.internal.ui.IInternalDebugUIConstants
import org.eclipse.debug.internal.ui.DebugPluginImages
import org.eclipse.ui.internal.console.IInternalConsoleConstants
import org.eclipse.ui.console.IConsoleConstants
import org.eclipse.ui.internal.console.ConsolePluginImages

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
    setToolTipText("Terminate and Relaunch")
    
    import IInternalDebugUIConstants._    
    setImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_ELCL_TERMINATE_AND_RELAUNCH))
    setDisabledImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_DLCL_TERMINATE_AND_RELAUNCH))
    setHoverImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_ELCL_TERMINATE_AND_RELAUNCH))
    
    override def run() {
      clearConsoleAction.run
      EclipseRepl.relaunchRepl(scalaProject)
    }
  }
  
  private object replayAction extends Action("Replay interpreter history") {
    setToolTipText("Replay all commands")
    
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
  
  private def setStarted {
    isStopped = false

    stopReplAction.setEnabled(true)
    relaunchAction.setEnabled(true)
    replayAction.setEnabled(true)
    
    inputField.setEnabled(true)

    setContentDescription("Scala REPL (Project: " + projectName + ")")
  }

  private def setStopped {
    isStopped = true

    stopReplAction.setEnabled(false)
    relaunchAction.setEnabled(false)
    replayAction.setEnabled(false)
    
    inputField.setEnabled(false)
    inputField.clearText()
    
    setContentDescription("<terminated> " + getContentDescription)
  }
    
  override def createPartControl(parent: Composite) {
    projectName = getViewSite.getSecondaryId
    if (projectName == null) projectName = ""
    
    codeBgColor = new Color(parent.getDisplay, 230, 230, 230) // light gray
    codeFgColor = new Color(parent.getDisplay, 64, 0, 128) // eggplant
    
    val panel = new Composite(parent, SWT.NONE)
    panel.setLayout(new GridLayout(2, false)) //two columns grid
     
    // 1st row
    textWidget = new StyledText(panel, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL)
    textWidget.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1)) // span two columns
    textWidget.setEditable(false)
    val editorFont = JFaceResources.getFont(PreferenceConstants.EDITOR_TEXT_FONT)    
    textWidget.setFont(editorFont) // java editor font
    
    // 2nd row
    val inputLabel = new Label(panel, SWT.NULL)
    inputLabel.setText("Evaluate:")
    
    inputField = new CommandField(panel, SWT.BORDER | SWT.SINGLE) {
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

//    createMenuActions
    
    setPartName("Scala REPL (" + projectName + ")")
    setStarted
  }
  
  private def createAction(name: String, payload: => Unit): Action = {
    new Action(name) {
      override def run() { payload }
    }
  }
  
  private def createMenuActions {
    val showImplicitsAction = createAction("Implicits in scope", { 
          displayOutput("to do: add hook for :implicits command. Note: has a verbose option\n")
        })
        
    val powerModeAction = createAction("Power user mode", { 
          displayOutput("to do: add hook for :power command. To do: add commands to dropdown: :dump, :phase, :wrap\n")
        })
   
    val menuManager = getViewSite.getActionBars.getMenuManager
    menuManager.add(showImplicitsAction)
    menuManager.add(powerModeAction)
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
    
    if (!isStopped)
      EclipseRepl.stopRepl(scalaProject, flush = false)
  }
}