/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.ui.internal.repl

import org.eclipse.swt.SWT
import org.eclipse.swt.custom.Bullet
import org.eclipse.swt.custom.ST
import org.eclipse.swt.custom.StyleRange
import org.eclipse.swt.events.ModifyEvent
import org.eclipse.swt.events.ModifyListener
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.events.SelectionListener
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.graphics.GlyphMetrics
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Display
import org.eclipse.swt.widgets.Event
import org.eclipse.swt.widgets.Listener
import org.eclipse.swt.widgets.Menu
import org.eclipse.swt.widgets.MenuItem
import org.eclipse.ui.ISharedImages
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.internal.console.ConsolePluginImages
import org.eclipse.ui.internal.console.IInternalConsoleConstants

class CommandFieldWithLineNumbersAndMenu(parent: Composite, style: Int) extends CommandField(parent, style) with InputContextMenuAndLineNumbers

trait InputContextMenuAndLineNumbers { self: CommandField =>

  protected var lineNumberBgColor: Color = new Color(self.getDisplay(), 230, 230, 230) // light gray
  protected var lineNumbersModifyListener: ModifyListener = null
  private var lineNumbersItem: MenuItem = null

  def setLineNumbersVisibility(visible: Boolean): Unit = {
    lineNumbersItem.setSelection(visible)
    updateLineNumbersVisibility()
  }

  private def initContextMenu() {
    val sharedImages = PlatformUI.getWorkbench().getSharedImages()
    val contextMenu = new Menu(self)
    self.setMenu(contextMenu)

    val cutItem = createCutMenuItem(contextMenu, sharedImages)
    val copyItem = createCopyMenuItem(contextMenu, sharedImages)
    createPasteMenuItem(contextMenu, sharedImages)

    new MenuItem(contextMenu, SWT.SEPARATOR)

    val clearItem = createClearMenuItem(contextMenu)
    val clearAfterEvaluationItem = createClearAfterEvaluationMenuItem(contextMenu)

    new MenuItem(contextMenu, SWT.SEPARATOR)

    createLineNumbersMenuItem(contextMenu)

    contextMenu.addListener(SWT.Show, new Listener() {
      override def handleEvent(event: Event) {
        val selection = self.getSelection()

        val textSelected = selection.x != selection.y
        cutItem.setEnabled(textSelected)
        copyItem.setEnabled(textSelected)

        clearItem.setEnabled(!self.isEmpty)
        clearAfterEvaluationItem.setSelection(self.clearTextAfterEvaluation)
      }
    })
  }

  private def createCutMenuItem(menu: Menu, sharedImages: ISharedImages) =
    createSimpleMenuItem(menu, "Cut", sharedImages.getImage(ISharedImages.IMG_TOOL_CUT), self.cut())

  private def createCopyMenuItem(menu: Menu, sharedImages: ISharedImages) =
    createSimpleMenuItem(menu, "Copy", sharedImages.getImage(ISharedImages.IMG_TOOL_COPY), self.copy())

  private def createPasteMenuItem(menu: Menu, sharedImages: ISharedImages) =
    createSimpleMenuItem(menu, "Paste", sharedImages.getImage(ISharedImages.IMG_TOOL_PASTE), self.paste())

  private def createClearMenuItem(menu: Menu) =
    createSimpleMenuItem(menu, "Clear", ConsolePluginImages.getImage(IInternalConsoleConstants.IMG_ELCL_CLEAR), self.clear())

  private def createLineNumbersMenuItem(menu: Menu): Unit = {
    lineNumbersItem = new MenuItem(menu, SWT.CHECK)
    lineNumbersItem.setText("Show Line Numbers")

    lineNumbersModifyListener = new ModifyListener() {
      override def modifyText(event: ModifyEvent) {
        redrawWithLineNumbers()
      }
    }

    lineNumbersItem.addSelectionListener(new SimpleSelectionListener(updateLineNumbersVisibility()))
  }

  private def createClearAfterEvaluationMenuItem(menu: Menu) = {
    val menuItem = new MenuItem(menu, SWT.CHECK)
    menuItem.setText("Clear text after evaluation")
    menuItem.addSelectionListener(new SimpleSelectionListener(self.clearTextAfterEvaluation = !self.clearTextAfterEvaluation))
    menuItem
  }

  private def createSimpleMenuItem(menu: Menu, text: String, image: Image, onSelected: => Unit) = {
    val menuItem = new MenuItem(menu, SWT.NORMAL)
    menuItem.setText(text)
    menuItem.setImage(image)
    menuItem.addSelectionListener(new SimpleSelectionListener(onSelected))
    menuItem
  }

  private def updateLineNumbersVisibility(): Unit = {
    if (lineNumbersItem.getSelection()) {
      self.addModifyListener(lineNumbersModifyListener)
      redrawWithLineNumbers()
    } else {
      self.removeModifyListener(lineNumbersModifyListener)
      self.setLineBullet(0, self.getLineCount(), null)
    }
  }

  private def redrawWithLineNumbers() {
    val maxLine = self.getLineCount()
    val lineCountWidth = Math.max(String.valueOf(maxLine).length(), 3)

    val style = new StyleRange();
    style.metrics = new GlyphMetrics(0, 0, lineCountWidth * 8 + 5)
    val bullet = new Bullet(ST.BULLET_NUMBER, style)
    val device = Display.getCurrent()

    bullet.style.background = lineNumberBgColor
    self.setLineBullet(0, self.getLineCount(), null) // clear current numbers
    self.setLineBullet(0, self.getLineCount(), bullet) // add new ones
  }

  override def dispose(): Unit = {
    if (lineNumberBgColor != null) lineNumberBgColor.dispose()
    self.dispose()
  }

  initContextMenu()
}

class SimpleSelectionListener(onSelected: => Unit) extends SelectionListener {
  override def widgetSelected(e: SelectionEvent): Unit = onSelected

  override def widgetDefaultSelected(e: SelectionEvent) {}
}
