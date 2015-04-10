/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.ui.internal.actions

import org.eclipse.core.commands.AbstractHandler
import org.eclipse.core.commands.ExecutionEvent
import org.eclipse.core.resources.IProject
import org.eclipse.jface.text.ITextSelection
import org.eclipse.ui.IFileEditorInput
import org.eclipse.ui.IWorkbenchPage
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.texteditor.ITextEditor
import org.scalaide.ui.internal.repl.ReplConsoleView
import org.scalaide.util.Utils.WithAsInstanceOfOpt

class RunSelectionInInterpreter extends RunSelection {
  override def doWithSelection(project: IProject, activePage: IWorkbenchPage, text: String): Unit =
    ReplConsoleView.makeVisible(project, activePage).evaluate(text)
}

abstract class RunSelection extends AbstractHandler {

  def doWithSelection(project: IProject, activePage: IWorkbenchPage, text: String): Unit

  override def execute(e: ExecutionEvent): AnyRef = {
    val workbenchWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow()

    for {
      editor <- Option(workbenchWindow.getActivePage.getActiveEditor)
      input <- editor.getEditorInput.asInstanceOfOpt[IFileEditorInput]
      textEditor <- editor.asInstanceOfOpt[ITextEditor]
      selection <- textEditor.getSelectionProvider.getSelection.asInstanceOfOpt[ITextSelection]
    } {
      val project = input.getFile.getProject
      var text = selection.getText

      if (text.isEmpty) {
        val provider = textEditor.getDocumentProvider
        provider.connect(input)

        try {
          val document = provider.getDocument(input)
          val lineOffset = document.getLineOffset(selection.getStartLine)
          val lineLength = document.getLineLength(selection.getStartLine)
          text = document.get(lineOffset, lineLength)
        } finally {
          provider.disconnect(input)
        }
      }

      if (!text.trim().isEmpty) doWithSelection(project, workbenchWindow.getActivePage, text)
    }

    null
  }
}
