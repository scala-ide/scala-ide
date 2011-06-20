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
    
    textWidget = new StyledText(parent, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL)
    textWidget.setEditable(false)
    textWidget.setFont(JFaceResources.getFont(PreferenceConstants.EDITOR_TEXT_FONT)) // java editor font
    
    val toolbarManager = getViewSite.getActionBars.getToolBarManager
    toolbarManager.add(stopReplAction)
    toolbarManager.add(relaunchAction)
    toolbarManager.add(new Separator)
    toolbarManager.add(clearConsoleAction)
    
    setPartName("Scala REPL (" + projectName + ")")
    setStarted
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
    codeBgColor.dispose
    codeFgColor.dispose
  }
}