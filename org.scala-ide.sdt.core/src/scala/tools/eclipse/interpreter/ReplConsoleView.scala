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
import org.eclipse.swt.widgets.Text
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout

// for the toolbar images
import org.eclipse.debug.internal.ui.IInternalDebugUIConstants
import org.eclipse.debug.internal.ui.DebugPluginImages
import org.eclipse.ui.internal.console.IInternalConsoleConstants
import org.eclipse.ui.console.IConsoleConstants
import org.eclipse.ui.internal.console.ConsolePluginImages

class ReplConsoleView extends ViewPart {

  var textWidget: StyledText = null
  var codeBgColor: Color = null
  var codeFgColor: Color = null
  var projectName: String = ""
  private var scalaProject: ScalaProject = null
  var isStopped = true
  var inputField: Text = null
   
  def setScalaProject(project: ScalaProject) {
    scalaProject = project
    
    if (isStopped) {
      clearConsoleAction.run
      setStarted
    }
  }
    
  object stopReplAction extends Action("Terminate") {
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
    
  object clearConsoleAction extends Action("Clear Output") {
    setToolTipText("Clear Output")
    setImageDescriptor(ConsolePluginImages.getImageDescriptor(IInternalConsoleConstants.IMG_ELCL_CLEAR));
    setDisabledImageDescriptor(ConsolePluginImages.getImageDescriptor(IInternalConsoleConstants.IMG_DLCL_CLEAR));
    setHoverImageDescriptor(ConsolePluginImages.getImageDescriptor(IConsoleConstants.IMG_LCL_CLEAR));   
    
    override def run() {
      textWidget.setText("")
      setEnabled(false)
    }
  }
  
  object relaunchAction extends Action("Relaunch Interpreter") {
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
  
  object replayAction extends Action("Replay interpreter history") {
    setToolTipText("Replay all commands")
    
    import IInternalDebugUIConstants._    
    setImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_ELCL_RESTART))
    setDisabledImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_DLCL_RESTART))
    setHoverImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_ELCL_RESTART))
    
    override def run() {
      displayOutput("To do: replay all commands without restarting interpreter")
    }
  }  
  
  private def setStarted {
    isStopped = false

    stopReplAction.setEnabled(true)
    relaunchAction.setEnabled(true)

    setContentDescription("Scala REPL (Project: " + projectName + ")")
  }

  private def setStopped {
    isStopped = true

    stopReplAction.setEnabled(false)
    relaunchAction.setEnabled(false)
    
    setContentDescription("<terminated> " + getContentDescription)
  }
    
  def createPartControl(parent: Composite) {
    projectName = getViewSite.getSecondaryId
    if (projectName == null) projectName = ""
    
    codeBgColor = new Color(parent.getDisplay, 230, 230, 230) // light gray
    codeFgColor = new Color(parent.getDisplay, 64, 0, 128) // eggplant
    
    val panel = new Composite(parent, SWT.NONE)
    panel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, true))
    panel.setLayout(new GridLayout)
      
    textWidget = new StyledText(panel, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL)
    textWidget.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true))
    textWidget.setEditable(false)
    val editorFont = JFaceResources.getFont(PreferenceConstants.EDITOR_TEXT_FONT)    
    textWidget.setFont(editorFont) // java editor font
    
    inputField = new Text(panel, SWT.BORDER | SWT.SINGLE)
    inputField.setFont(editorFont)
    inputField.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false))
     
    val toolbarManager = getViewSite.getActionBars.getToolBarManager
    toolbarManager.add(replayAction)
    toolbarManager.add(new Separator)
    toolbarManager.add(stopReplAction)
    toolbarManager.add(relaunchAction)
    toolbarManager.add(new Separator)
    toolbarManager.add(clearConsoleAction)

    createMenuActions
    
    setPartName("Scala REPL (" + projectName + ")")
    setStarted
  }
  
  private def createAction(name: String, payload: => Unit): Action = {
    new Action(name) {
      override def run() { payload }
    }
  }
  
  def createMenuActions {
    val showImportsAction = createAction("Import history", { 
          displayOutput("to do: add hook for :imports command. Note: will take a string argument\n")
        })
        
    val showImplicitsAction = createAction("Implicits in scope", { 
          displayOutput("to do: add hook for :implicits command. Note: has a verbose option\n")
        })
        
    val powerModeAction = createAction("Power user mode", { 
          displayOutput("to do: add hook for :power command. To do: add commands to dropdown: :dump, :phase, :wrap\n")
        })
   
    val typeAction = createAction("Show type", { 
          displayOutput("to do: add hook for :type command. Note: takes an argument. TBD: make this a right-click menu item?\n")
        })
    
    val menuManager = getViewSite.getActionBars.getMenuManager
    menuManager.add(showImportsAction)
    menuManager.add(showImplicitsAction)
    menuManager.add(typeAction)
    menuManager.add(powerModeAction)
  }

  def setFocus() { }
       
  /**
   * Display the string with code formatting
   */
  def displayCode(text: String) {
    if (textWidget.getCharCount != 0) // don't insert a newline if this is the first line of code to be displayed
      displayOutput("\n")
    appendText(text, codeFgColor, codeBgColor, SWT.ITALIC, insertNewline = true)
    displayOutput("\n")
  }

  def displayOutput(text: String) {
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
    stopReplAction.run // FIXME: this should NOT WRITE anything to the widget because it will be disposed already
    codeBgColor.dispose
    codeFgColor.dispose
  }
}