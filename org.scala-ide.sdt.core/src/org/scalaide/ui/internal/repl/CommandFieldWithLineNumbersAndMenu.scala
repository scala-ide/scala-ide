/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.ui.internal.repl

import org.eclipse.jface.resource.JFaceResources
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.Bullet
import org.eclipse.swt.custom.ST
import org.eclipse.swt.custom.StyleRange
import org.eclipse.swt.events.ModifyListener
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.graphics.GlyphMetrics
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Event
import org.eclipse.swt.widgets.Listener
import org.eclipse.swt.widgets.Menu
import org.eclipse.swt.widgets.MenuItem
import org.eclipse.ui.ISharedImages
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.internal.console.ConsolePluginImages
import org.eclipse.ui.internal.console.IInternalConsoleConstants
import org.scalaide.util.eclipse.SWTUtils._

class CommandFieldWithLineNumbersAndMenu(parent: Composite, style: Int)
  extends CommandField(parent, style)
  with InputContextMenuAndLineNumbers

trait InputContextMenuAndLineNumbers extends CommandField {

  protected val lineNumberBgColor: Color = JFaceResources.getColorRegistry.get(InterpreterConsoleView.LineNumberBackgroundColor)
  protected val lineNumbersModifyListener: ModifyListener = () => redrawWithLineNumbers()
  protected def onLineNumbersVisibilityUpdated(enabled: Boolean): Unit = {}
  private val lineNumbersItem: MenuItem = initContextMenuAndReturnLineNumbersItem

  setBackground(JFaceResources.getColorRegistry.get(InterpreterConsoleView.BackgroundColor))
  setForeground(JFaceResources.getColorRegistry.get(InterpreterConsoleView.ForegroundColor))

  def setLineNumbersVisibility(visible: Boolean): Unit = {
    lineNumbersItem.setSelection(visible)
    updateLineNumbersVisibility()
  }

  private def initContextMenuAndReturnLineNumbersItem() = {
    val sharedImages = PlatformUI.getWorkbench().getSharedImages()
    val contextMenu = new Menu(this)
    setMenu(contextMenu)

    val cutItem = createCutMenuItem(contextMenu, sharedImages)
    val copyItem = createCopyMenuItem(contextMenu, sharedImages)
    createPasteMenuItem(contextMenu, sharedImages)

    new MenuItem(contextMenu, SWT.SEPARATOR)

    val clearItem = createClearMenuItem(contextMenu)
    val clearAfterEvaluationItem = createClearAfterEvaluationMenuItem(contextMenu)

    new MenuItem(contextMenu, SWT.SEPARATOR)

    val lineNumbersMenuItem = createLineNumbersMenuItem(contextMenu)

    contextMenu.addListener(SWT.Show, new Listener() {
      override def handleEvent(event: Event) = {
        val selection = getSelection()

        val textSelected = selection.x != selection.y
        cutItem.setEnabled(textSelected)
        copyItem.setEnabled(textSelected)

        clearItem.setEnabled(!isEmpty)
        clearAfterEvaluationItem.setSelection(clearTextAfterEvaluation)
      }
    })

    lineNumbersMenuItem
  }

  private def createCutMenuItem(menu: Menu, sharedImages: ISharedImages) =
    createSimpleMenuItem(menu, "Cut", sharedImages.getImage(ISharedImages.IMG_TOOL_CUT), cut())

  private def createCopyMenuItem(menu: Menu, sharedImages: ISharedImages) =
    createSimpleMenuItem(menu, "Copy", sharedImages.getImage(ISharedImages.IMG_TOOL_COPY), copy())

  private def createPasteMenuItem(menu: Menu, sharedImages: ISharedImages) =
    createSimpleMenuItem(menu, "Paste", sharedImages.getImage(ISharedImages.IMG_TOOL_PASTE), paste())

  private def createClearMenuItem(menu: Menu) =
    createSimpleMenuItem(menu, "Clear", ConsolePluginImages.getImage(IInternalConsoleConstants.IMG_ELCL_CLEAR), clear())

  private def createLineNumbersMenuItem(menu: Menu) = {
    val lineNumbersItem = new MenuItem(menu, SWT.CHECK)
    lineNumbersItem.setText("Show Line Numbers")

    lineNumbersItem.addSelectionListener(() => updateLineNumbersVisibility())
    lineNumbersItem
  }

  private def createClearAfterEvaluationMenuItem(menu: Menu) = {
    val menuItem = new MenuItem(menu, SWT.CHECK)
    menuItem.setText("Clear Text After Evaluation")
    menuItem.addSelectionListener(() => clearTextAfterEvaluation = !clearTextAfterEvaluation)
    menuItem
  }

  private def createSimpleMenuItem(menu: Menu, text: String, image: Image, onSelected: => Unit) = {
    val menuItem = new MenuItem(menu, SWT.NORMAL)
    menuItem.setText(text)
    menuItem.setImage(image)
    menuItem.addSelectionListener(() => onSelected)
    menuItem
  }

  private def updateLineNumbersVisibility(): Unit = {
    val lineNumbersEnabled = lineNumbersItem.getSelection()
    if (lineNumbersEnabled) {
      addModifyListener(lineNumbersModifyListener)
      redrawWithLineNumbers()
    } else {
      removeModifyListener(lineNumbersModifyListener)
      setLineBullet(0, getLineCount(), null)
    }
    onLineNumbersVisibilityUpdated(lineNumbersEnabled)
  }

  private def redrawWithLineNumbers() = {
    val maxLine = getLineCount()
    val lineCountWidth = Math.max(String.valueOf(maxLine).length(), 3)

    val style = new StyleRange()
    style.metrics = new GlyphMetrics(0, 0, lineCountWidth * 8 + 5)
    val bullet = new Bullet(ST.BULLET_NUMBER, style)

    bullet.style.background = lineNumberBgColor
    setLineBullet(0, getLineCount(), null) // clear current numbers
    setLineBullet(0, getLineCount(), bullet) // add new ones
  }
}
