/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.ui.internal.repl

import org.scalaide.core.IScalaPlugin
import org.scalaide.ui.syntax.ScalariformToSyntaxClass
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.ui.PreferenceConstants
import org.eclipse.jface.resource.JFaceResources
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.SashForm
import org.eclipse.swt.custom.StyleRange
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.graphics.Font
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.widgets.Caret
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Display
import org.eclipse.ui.part.ViewPart

import scalariform.lexer.ScalaLexer

/**
 * A split horizontal view for enter scala commands and displaying REPL output.
 *
 * This UI component contains a sash form with the top widget being a console-output like text view
 * and the bottom view being an instance of `CommandField` for entering scala expressions.
 */
trait InterpreterConsoleView extends ViewPart {
  protected var interpreterPanel: SashForm = null
  protected var resultsTextWidget: StyledTextWithSimpleMenu = null
  protected var inputCommandField: CommandFieldWithLineNumbersAndMenu = null
  protected var codeBgColor: Color = null
  protected var codeFgColor: Color = null
  protected var errorFgColor: Color = null
  protected var display: Display = null
  protected def doOnLineNumbersVisibilityUpdate(enabled: Boolean): Unit = {}

  protected def createCommandField(parent: Composite, suggestedStyles: Seq[Int]): CommandFieldWithLineNumbersAndMenu = {
    new CommandFieldWithLineNumbersAndMenu(parent, suggestedStyles.reduce((l, r) => l | r)) {
      override protected def helpText = "<type an expression>\tCTRL+ENTER to evaluate\nto browse expressions from history use:\tCTRL+Up and CTRL+Down"
      setEvaluator(new CommandField.Evaluator {
        override def eval(command: String) = evaluate(command)
      })

      override def onLineNumbersVisibilityUpdated(enabled: Boolean): Unit = doOnLineNumbersVisibilityUpdate(enabled)
    }
  }

  /** Override to perform some specific work (such as performing evaluation and updating the top output) on scala command evaluation */
  protected def evaluate(command: String): Unit

  /**
   * Creates view with uneditable text field for result and editable text field for input
   */
  protected def createInterpreterPartControl(parent: Composite): Unit = {
    display = parent.getDisplay()
    codeBgColor = new Color(display, 230, 230, 230) // light gray
    codeFgColor = new Color(display, 60, 0, 128) // eggplant
    errorFgColor = new Color(display, 128, 0, 64) // maroon

    interpreterPanel = new SashForm(parent, SWT.VERTICAL)
    interpreterPanel.setLayout(new FillLayout)

    // 1st row
    resultsTextWidget = new StyledTextWithSimpleMenu(interpreterPanel, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL)
    resultsTextWidget.setLayout(new FillLayout)
    resultsTextWidget.setEditable(false)
    resultsTextWidget.setCaret(new Caret(resultsTextWidget, SWT.NONE))
    resultsTextWidget.setAlwaysShowScrollBars(false)

    val editorFont = JFaceResources.getFont(PreferenceConstants.EDITOR_TEXT_FONT)
    resultsTextWidget.setFont(editorFont) // java editor font

    // 2nd row
    inputCommandField = createCommandField(interpreterPanel, Seq(SWT.BORDER, SWT.MULTI, SWT.H_SCROLL, SWT.V_SCROLL, SWT.RESIZE))
    inputCommandField.setFont(editorFont)
    inputCommandField.setLayout(new FillLayout)
    inputCommandField.setAlwaysShowScrollBars(false)

    interpreterPanel.setWeights(Array(3, 1))
  }

  /**
   * Display the string with code formatting
   */
  protected def displayCode(text: String) = displayPadded(codeBgColor) {
    val prefStore = IScalaPlugin().getPreferenceStore
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
    val fontData = resultsTextWidget.getFont().getFontData()
    fontData.foreach(_.setHeight(4))
    val font = new Font(display, fontData)
    appendText(if (isTop) "\n " else " \n", null, bgColor, SWT.NORMAL, font = font)
  }

  protected def appendText(text: String, fgColor: Color, bgColor: Color, fontStyle: Int, font: Font = null, insertNewline: Boolean = false) {
    val lastOffset = resultsTextWidget.getCharCount
    val oldLastLine = resultsTextWidget.getLineCount

    val outputStr =
      if (insertNewline) "\n" + text.stripLineEnd + "\n\n"
      else text

    resultsTextWidget.append(outputStr)
    val style1 = new StyleRange(lastOffset, outputStr.length, fgColor, null, fontStyle)
    style1.font = font
    resultsTextWidget.setStyleRange(style1)

    val lastLine = resultsTextWidget.getLineCount
    if (bgColor != null)
      resultsTextWidget.setLineBackground(oldLastLine - 1, lastLine - oldLastLine, bgColor)
    resultsTextWidget.setTopIndex(resultsTextWidget.getLineCount - 1)
    val style2 = new StyleRange(lastOffset, outputStr.length, fgColor, null, fontStyle)
    style2.font = font
    resultsTextWidget.setStyleRange(style2)
  }

  override def dispose(): Unit = {
    if (codeBgColor != null) codeBgColor.dispose()
    if (codeFgColor != null) codeFgColor.dispose()
    if (errorFgColor != null) errorFgColor.dispose()
    if (interpreterPanel != null) interpreterPanel.dispose()
    if (inputCommandField != null) inputCommandField.dispose()
    if (resultsTextWidget != null) resultsTextWidget.dispose()
  }
}
