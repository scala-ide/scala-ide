/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.ui.internal.repl

import org.scalaide.core.ScalaPlugin
import org.scalaide.ui.syntax.ScalariformToSyntaxClass
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
import org.eclipse.swt.events.ModifyListener
import org.eclipse.core.resources.IProject
import org.eclipse.debug.internal.ui.DebugPluginImages
import org.eclipse.debug.internal.ui.IInternalDebugUIConstants
import org.eclipse.jface.action.Action
import org.eclipse.jface.action.Separator
import org.eclipse.jface.bindings.keys.KeyStroke
import org.eclipse.jface.fieldassist.ContentProposalAdapter
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.Bullet
import org.eclipse.swt.custom.ST
import org.eclipse.swt.custom.StyleRange
import org.eclipse.swt.custom.VerifyKeyListener
import org.eclipse.swt.events.ModifyEvent
import org.eclipse.swt.events.ModifyListener
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.events.SelectionListener
import org.eclipse.swt.events.VerifyEvent
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.graphics.GlyphMetrics
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.graphics.RGB
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Display
import org.eclipse.swt.widgets.Event
import org.eclipse.swt.widgets.Listener
import org.eclipse.swt.widgets.Menu
import org.eclipse.swt.widgets.MenuItem
import org.eclipse.ui.ISharedImages
import org.eclipse.ui.IWorkbenchPage
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.console.IConsoleConstants
import org.eclipse.ui.internal.console.ConsolePluginImages
import org.eclipse.ui.internal.console.IInternalConsoleConstants
import org.eclipse.ui.part.ViewPart

/**
 * A split horizontal view for enter scala commands and displaying REPL output.
 *
 * This UI component contains a sash form with the top widget being a console-output like text view
 * and the bottom view being an instance of `CommandField` for entering scala expressions.
 */
trait InterpreterConsoleView { self: ViewPart =>
  protected var resultsTextWidget: StyledText = null
  protected var inputCommandField: CommandField = null
  protected var codeBgColor: Color = null
  protected var codeFgColor: Color = null
  protected var errorFgColor: Color = null
  protected var lineNumberBgColor: Color = null
  protected var display: Display = null
  protected var lineNumberModifyListener: ModifyListener = null

  protected def createCommandField(parent: Composite, suggestedStyles: Seq[Int]): CommandField = {
    new CommandField(parent, suggestedStyles.reduce((l, r) => l | r)) {
      override protected def helpText = "<type an expression>\tCTRL+ENTER to evaluate\nto browse expressions from history use:\tCTRL+Up and CTRL+Down"
      setEvaluator(new CommandField.Evaluator {
        override def eval(command: String) = evaluate(command)
      })
    }
  }

  /** Override to perform some specific work (such as performing evaluation and updating the top output) on scala command evaluation */
  protected def evaluate(command: String)

  protected def createInterpreterPartControl(parent: Composite) = {
    display = parent.getDisplay()
    codeBgColor = new Color(display, 230, 230, 230) // light gray
    codeFgColor = new Color(display, 60, 0, 128) // eggplant
    errorFgColor = new Color(display, 128, 0, 64) // maroon
    lineNumberBgColor = new Color(display, 230, 230, 230) // light gray

    val panel = new SashForm(parent, SWT.VERTICAL)
    panel.setLayout(new FillLayout)

    // 1st row
    resultsTextWidget = new StyledText(panel, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL)
    resultsTextWidget.setLayout(new FillLayout)
    resultsTextWidget.setEditable(false)
    resultsTextWidget.setCaret(new Caret(resultsTextWidget, SWT.NONE))
    resultsTextWidget.setAlwaysShowScrollBars(false)

    val editorFont = JFaceResources.getFont(PreferenceConstants.EDITOR_TEXT_FONT)
    resultsTextWidget.setFont(editorFont) // java editor font

    // 2nd row
    inputCommandField = createCommandField(panel, Seq(SWT.BORDER, SWT.MULTI, SWT.H_SCROLL, SWT.V_SCROLL, SWT.RESIZE))
    inputCommandField.setFont(editorFont)
    inputCommandField.setLayout(new FillLayout)
    inputCommandField.setAlwaysShowScrollBars(false)

    panel.setWeights(Array(3, 1))

    initContextMenus()
  }

  /**
   * Display the string with code formatting
   */
  protected def displayCode(text: String) = displayPadded(codeBgColor) {
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

  override def dispose() = {
    if (codeBgColor != null) codeBgColor.dispose()
    if (codeFgColor != null) codeFgColor.dispose()
    if (errorFgColor != null) errorFgColor.dispose()
    if (lineNumberBgColor != null) lineNumberBgColor.dispose()
  }

  def setFocus()

  protected def initContextMenus() {
    val sharedImages = PlatformUI.getWorkbench().getSharedImages()
    initResultContextMenu(sharedImages)
    initInputContextMenu(sharedImages)
  }

  private def initResultContextMenu(sharedImages: ISharedImages) {
    val resultContextMenu = new Menu(resultsTextWidget)

    val resultCopyItem = new MenuItem(resultContextMenu, SWT.NORMAL)
    resultCopyItem.setText("Copy")
    resultCopyItem.setImage(sharedImages.getImage(ISharedImages.IMG_TOOL_COPY))

    new MenuItem(resultContextMenu, SWT.SEPARATOR)
    val resultClearItem = new MenuItem(resultContextMenu, SWT.NORMAL)
    resultClearItem.setText("Clear")
    resultClearItem.setImage(ConsolePluginImages.getImage(IInternalConsoleConstants.IMG_ELCL_CLEAR))

    resultsTextWidget.setMenu(resultContextMenu)

    resultContextMenu.addListener(SWT.Show, new Listener() {
      override def handleEvent(event: Event) {
        val selection = resultsTextWidget.getSelection()

        val textSelected = selection.x != selection.y
        resultCopyItem.setEnabled(textSelected)

        resultClearItem.setEnabled(resultsTextWidget.getCharCount() > 0)
      }
    })

    resultCopyItem.addSelectionListener(new SimpleSelectionListener(resultsTextWidget.copy()))
    resultClearItem.addSelectionListener(new SimpleSelectionListener(resultsTextWidget.setText("")))
  }

  private def initInputContextMenu(sharedImages: ISharedImages) {
    val inputContextMenu = new Menu(inputCommandField)

    val inputCutItem = new MenuItem(inputContextMenu, SWT.NORMAL)
    inputCutItem.setText("Cut")
    inputCutItem.setImage(sharedImages.getImage(ISharedImages.IMG_TOOL_CUT))

    val inputCopyItem = new MenuItem(inputContextMenu, SWT.NORMAL)
    inputCopyItem.setText("Copy")
    inputCopyItem.setImage(sharedImages.getImage(ISharedImages.IMG_TOOL_COPY))

    val inputPasteItem = new MenuItem(inputContextMenu, SWT.NORMAL)
    inputPasteItem.setText("Paste")
    inputPasteItem.setImage(sharedImages.getImage(ISharedImages.IMG_TOOL_PASTE))

    new MenuItem(inputContextMenu, SWT.SEPARATOR)

    val inputClearItem = new MenuItem(inputContextMenu, SWT.NORMAL)
    inputClearItem.setText("Clear")
    inputClearItem.setImage(ConsolePluginImages.getImage(IInternalConsoleConstants.IMG_ELCL_CLEAR))

    new MenuItem(inputContextMenu, SWT.SEPARATOR)
    val lineNumbersItem = new MenuItem(inputContextMenu, SWT.CHECK)
    lineNumbersItem.setText("Show Line Numbers")

    inputCommandField.setMenu(inputContextMenu)

    inputContextMenu.addListener(SWT.Show, new Listener() {
      override def handleEvent(event: Event) {
        val selection = inputCommandField.getSelection()

        val textSelected = selection.x != selection.y
        inputCutItem.setEnabled(textSelected)
        inputCopyItem.setEnabled(textSelected)

        inputClearItem.setEnabled(!inputCommandField.isEmpty)
      }
    })

    lineNumberModifyListener = new ModifyListener() {
      override def modifyText(event: ModifyEvent) {
        redrawWithLineNumbers()
      }
    }

    lineNumbersItem.addSelectionListener(new SimpleSelectionListener(switchLineNumberModifyListener(lineNumbersItem)))
    inputCutItem.addSelectionListener(new SimpleSelectionListener(inputCommandField.cut()))
    inputCopyItem.addSelectionListener(new SimpleSelectionListener(inputCommandField.copy()))
    inputPasteItem.addSelectionListener(new SimpleSelectionListener(inputCommandField.paste()))
    inputClearItem.addSelectionListener(new SimpleSelectionListener(inputCommandField.setText("")))
  }

  private def switchLineNumberModifyListener(lineNumbersItem: MenuItem) {
    if (lineNumbersItem.getSelection()) {
      inputCommandField.addModifyListener(lineNumberModifyListener)
      redrawWithLineNumbers()
    } else {
      inputCommandField.removeModifyListener(lineNumberModifyListener)
      inputCommandField.setLineBullet(0, inputCommandField.getLineCount(), null)
    }
  }

  private def redrawWithLineNumbers() {
    val maxLine = inputCommandField.getLineCount()
    val lineCountWidth = Math.max(String.valueOf(maxLine).length(), 3)

    val style = new StyleRange();
    style.metrics = new GlyphMetrics(0, 0, lineCountWidth * 8 + 5)
    val bullet = new Bullet(ST.BULLET_NUMBER, style)
    val device = Display.getCurrent()

    bullet.style.background = lineNumberBgColor
    inputCommandField.setLineBullet(0, inputCommandField.getLineCount(), null) // clear current numbers
    inputCommandField.setLineBullet(0, inputCommandField.getLineCount(), bullet) // add new ones
  }
}

class SimpleSelectionListener(onSelected: => Unit) extends SelectionListener {
  override def widgetSelected(e: SelectionEvent): Unit = onSelected

  override def widgetDefaultSelected(e: SelectionEvent) {}
}
