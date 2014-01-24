package org.scalaide.editor

import org.eclipse.jface.text.IDocument

import org.eclipse.ui.texteditor.ITextEditor

object Editor {
  /** Return the document associated with the given editor, if possible. */
  def getEditorDocument(editor: ITextEditor): Option[IDocument] =
    Option(editor.getDocumentProvider()).map(_.getDocument(editor.getEditorInput()))
}