/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.ui.internal.repl

import org.eclipse.swt.custom.StyledText
import org.eclipse.swt.widgets.Menu
import org.eclipse.swt.widgets.MenuItem
import org.eclipse.ui.internal.console.ConsolePluginImages
import org.eclipse.swt.SWT
import org.eclipse.ui.internal.console.IInternalConsoleConstants
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.ISharedImages
import org.eclipse.swt.widgets.Listener
import org.eclipse.swt.widgets.Event
import org.eclipse.swt.widgets.Composite

class StyledTextWithSimpleMenu(parent: Composite, style: Int) extends StyledText(parent, style) with CopyAndClearMenu

/**
 * Provides context menu with copy and clear options
 */
trait CopyAndClearMenu { self: StyledText =>

  def clear() = self.setText("")

  private def initContextMenu() {
    val contextMenu = new Menu(self)

    val copyTextItem = new MenuItem(contextMenu, SWT.NORMAL)
    copyTextItem.setText("Copy")
    val sharedImages = PlatformUI.getWorkbench().getSharedImages()
    copyTextItem.setImage(sharedImages.getImage(ISharedImages.IMG_TOOL_COPY))

    new MenuItem(contextMenu, SWT.SEPARATOR)
    val clearTextItem = new MenuItem(contextMenu, SWT.NORMAL)
    clearTextItem.setText("Clear")
    clearTextItem.setImage(ConsolePluginImages.getImage(IInternalConsoleConstants.IMG_ELCL_CLEAR))

    self.setMenu(contextMenu)

    contextMenu.addListener(SWT.Show, new Listener() {
      override def handleEvent(event: Event) {
        val selection = self.getSelection()

        val textSelected = selection.x != selection.y
        copyTextItem.setEnabled(textSelected)

        clearTextItem.setEnabled(self.getCharCount() > 0)
      }
    })

    copyTextItem.addSelectionListener(new SimpleSelectionListener(self.copy()))
    clearTextItem.addSelectionListener(new SimpleSelectionListener(clear()))
  }

  initContextMenu()
}
