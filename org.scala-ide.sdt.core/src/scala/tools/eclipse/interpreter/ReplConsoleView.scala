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
    
  object stopReplAction extends Action("Terminate") {
    setToolTipText("Terminate")
    
    import IInternalDebugUIConstants._
    setImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_LCL_TERMINATE))
    setDisabledImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_DLCL_TERMINATE))
    setHoverImageDescriptor(DebugPluginImages.getImageDescriptor(IMG_LCL_TERMINATE))
    
    override def run() {
      println("*** stop repl action.run was called")
    }
    
    setEnabled(false) // TODO change
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
      println("*** relaunch interpreter action called")
    }
    
    setEnabled(false) // TODO change
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
    setContentDescription("Scala REPL (Project: " + projectName + ")")
  }

  def setFocus() { }
       
  /**
   * Display the string with code formatting
   */
  def displayCode(text: String) {
    appendText(text, codeFgColor, codeBgColor, SWT.ITALIC)
  }

  def displayOutput(text: String) {
    appendText(text, null, null, SWT.NORMAL)
  }
  
  private def appendText(text: String, fgColor: Color, bgColor: Color, fontStyle: Int) {
    val lastOffset = textWidget.getCharCount
    val oldLastLine = textWidget.getLineCount
    
    textWidget.append("\n")
    textWidget.append(text)
    textWidget.append("\n")
    textWidget.setStyleRange(new StyleRange(lastOffset, text.length + 1, fgColor, null, fontStyle))
    
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