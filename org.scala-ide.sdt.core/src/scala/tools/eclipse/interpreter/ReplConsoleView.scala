package scala.tools.eclipse
package interpreter

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

class ReplConsoleView extends ViewPart {

  var textWidget: StyledText = null
  var codeBgColor: Color = null
  var codeFgColor: Color = null
  var projectName: String = ""
  
  def createPartControl(parent: Composite) {
    codeBgColor = new Color(parent.getDisplay, 230, 230, 230) // light gray
    codeFgColor = new Color(parent.getDisplay, 64, 0, 128) // eggplant
    
    textWidget = new StyledText(parent, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL)
    textWidget.setEditable(false)
    textWidget.setFont(JFaceResources.getFont(PreferenceConstants.EDITOR_TEXT_FONT)) // java editor font
  }

  def setFocus() { }
   
  override def getTitle(): String = "Scala REPL: Project " + projectName

  override def getTitleToolTip(): String = "Scala REPL for project " + projectName
    
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
  }
  
  override def dispose() {
    codeBgColor.dispose
    codeFgColor.dispose
  }
}