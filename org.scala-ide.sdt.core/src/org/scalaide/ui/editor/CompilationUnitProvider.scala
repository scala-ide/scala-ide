package org.scalaide.ui.editor

import org.eclipse.core.resources.IFile
import org.eclipse.ui.IEditorInput
import org.eclipse.ui.part.FileEditorInput
import org.eclipse.ui.texteditor.ITextEditor
import org.eclipse.jface.text.IDocument

/** Abstract provider for compilation units.
  *
  * @note This class is thread-safe.
  */
abstract class CompilationUnitProvider[T <: CompilationUnit] {
  /** Returns a new compilation unit for the passed `editor` */
  def fromEditor(editor: ITextEditor): T = {
    val input = editor.getEditorInput
    if (input == null)
      throw new NullPointerException("No editor input for the passed `editor`. Hint: Maybe the editor isn't yet fully initialized?")
    else {
      val unit = mkCompilationUnit(getFile(input, fileExtension))
      unit.connect(editor.getDocumentProvider().getDocument(input))
      unit
    }
  }

  def fromFileAndDocument(file: IFile, doc: IDocument): T = {
    val unit = mkCompilationUnit(file)
    unit.connect(doc)
    unit
  }

  private def getFile(editorInput: IEditorInput, fileExtension: String): IFile = {
    editorInput match {
      case fileEditorInput: FileEditorInput if fileEditorInput.getName.endsWith(fileExtension) =>
        fileEditorInput.getFile
      case _ => throw new IllegalArgumentException("Expected to open file with extension %s, found %s.".format(fileExtension, editorInput.getName))
    }
  }

  protected def mkCompilationUnit(workspaceFile: IFile): T

  /** Expected file extension. */
  protected def fileExtension: String
}
