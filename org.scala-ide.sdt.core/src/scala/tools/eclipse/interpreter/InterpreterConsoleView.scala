package scala.tools.eclipse.interpreter

import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.properties.syntaxcolouring.ScalariformToSyntaxClass
import scala.tools.eclipse.ui.CommandField
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.ui.PreferenceConstants
import org.eclipse.jface.action.Action
import org.eclipse.jface.resource.JFaceResources
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.SashForm
import org.eclipse.swt.custom.StyleRange
import org.eclipse.swt.custom.StyledText
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.widgets.Caret
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Display
import org.eclipse.ui.console.IConsoleConstants
import org.eclipse.ui.internal.console.ConsolePluginImages
import org.eclipse.ui.internal.console.IInternalConsoleConstants
import org.eclipse.ui.part.ViewPart
import scalariform.lexer.ScalaLexer
import org.eclipse.swt.graphics.TextStyle
import org.eclipse.swt.graphics.Font

/**
 * A split horizontal split view for enter scala commands and displaying REPL output.
 *
 * This UI component contains a sash form with the top widget being a console-output like text view
 * and the bottom view being an instance of `CommandField` for entering scala expressions.
 */
trait InterpreterConsoleView { self: ViewPart =>
  protected var textWidget: StyledText = null
  protected var codeBgColor: Color = null
  protected var codeFgColor: Color = null
  protected var errorFgColor: Color = null
  protected var display: Display = null

  protected def createCommandField(parent: Composite, suggestedStyles: Seq[Int]): CommandField = {
    new CommandField(parent, suggestedStyles.reduce((l, r) => l | r)) {
      override protected def helpText = "<type an expression>\tCTRL+ENTER to evaluate"
      setEvaluator(new scala.tools.eclipse.ui.CommandField.Evaluator {
        override def eval(command: String) = evaluate(command)
      })
    }
  }

  /** Override to perform some specific work (such as performing evaluation and updating the top output) on scala command evaluation */
  protected def evaluate(command: String) {}

  protected def createInterpreterPartControl(parent: Composite) = {
    display = parent.getDisplay()
    codeBgColor = new Color(display, 230, 230, 230) // light gray
    codeFgColor = new Color(display, 60, 0, 128) // eggplant
    errorFgColor = new Color(display, 128, 0, 64) // maroon

    val panel = new SashForm(parent, SWT.VERTICAL)
    panel.setLayout(new FillLayout)

    // 1st row
    textWidget = new StyledText(panel, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL)
    textWidget.setLayout(new FillLayout)
    textWidget.setEditable(false)
    textWidget.setCaret(new Caret(textWidget, SWT.NONE))
    textWidget.setAlwaysShowScrollBars(false)

    val editorFont = JFaceResources.getFont(PreferenceConstants.EDITOR_TEXT_FONT)
    textWidget.setFont(editorFont) // java editor font

    // 2nd row
    val inputField = createCommandField(panel, Seq(SWT.BORDER, SWT.MULTI, SWT.H_SCROLL, SWT.V_SCROLL, SWT.RESIZE))
    inputField.setFont(editorFont)
    inputField.setLayout(new FillLayout)
    inputField.setAlwaysShowScrollBars(false)

    panel.setWeights(Array(3, 1))
  }

  /**
   * Display the string with code formatting
   */
  protected def displayCode(text: String) = displayPadded(codeBgColor) {
    val colorManager = JavaPlugin.getDefault.getJavaTextTools.getColorManager
    val prefStore = ScalaPlugin.plugin.getPreferenceStore
    for (token <- ScalaLexer.rawTokenise(text, forgiveErrors = true)) {
      val textAttribute = ScalariformToSyntaxClass(token).getTextAttribute(prefStore)
      val bgColor = Option(textAttribute.getBackground) getOrElse codeBgColor
      appendText(token.text, textAttribute.getForeground, bgColor, textAttribute.getStyle, insertNewline = false)
    }
    appendText("\n", codeFgColor, codeBgColor, SWT.NORMAL, insertNewline = false)
  }

  protected def displayOutput(text: String) = displayPadded(null) {
    appendText(text, null, null, SWT.NORMAL)
  }

  protected def displayError(text: String) = displayPadded(null) {
    appendText(text, errorFgColor, null, SWT.NORMAL)
  }

  protected def displayPadded(bgColor: Color)(display: => Unit) {
    insertSpacing(bgColor, true)
    display
    insertSpacing(bgColor, false)
  }

  private def insertSpacing(bgColor: Color, isTop: Boolean) {
    val fontData = textWidget.getFont().getFontData()
    fontData.foreach(_.setHeight(4))
    val font = new Font(display, fontData)
    appendText(if (isTop) "\n " else " \n", null, bgColor, SWT.NORMAL, font = font)
  }

  protected def appendText(text: String, fgColor: Color, bgColor: Color, fontStyle: Int, font: Font = null, insertNewline: Boolean = false) {
    val lastOffset = textWidget.getCharCount
    val oldLastLine = textWidget.getLineCount

    val outputStr =
      if (insertNewline) "\n" + text.stripLineEnd + "\n\n"
      else text

    textWidget.append(outputStr)
    val style1 = new StyleRange(lastOffset, outputStr.length, fgColor, null, fontStyle)
    style1.font = font
    textWidget.setStyleRange(style1)

    val lastLine = textWidget.getLineCount
     if (bgColor != null)
      textWidget.setLineBackground(oldLastLine - 1, lastLine - oldLastLine, bgColor)
    textWidget.setTopIndex(textWidget.getLineCount - 1)
    val style2 = new StyleRange(lastOffset, outputStr.length, fgColor, null, fontStyle)
    style2.font = font
    textWidget.setStyleRange(style2)
  }

  override def dispose() = {
    if (codeBgColor != null) codeBgColor.dispose()
    if (codeFgColor != null) codeFgColor.dispose()
    if (errorFgColor != null) errorFgColor.dispose()
  }

  override def setFocus() = {}
}