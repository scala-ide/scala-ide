package org.scalaide.ui.internal.editor.macros

import org.eclipse.ui.texteditor.AbstractDecoratedTextEditor
import org.eclipse.ui.IEditorInput
import org.eclipse.jface.viewers.ISelection
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.jface.text.IDocument
import org.scalaide.logging.HasLogger
import org.scalaide.ui.internal.editor.ScalaSourceFileEditor
import org.eclipse.ui.part.FileEditorInput
import org.scalaide.ui.internal.editor.decorators.macros.Marker2Expand
import org.eclipse.core.resources.IMarker
import org.eclipse.jface.text.Position
import org.scalaide.ui.internal.editor.decorators.macros.ScalaMacroMarker
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.TextUtilities

trait MacroCreateMarker {
  def editorInput: IEditorInput

  def createMacroMarker(markerId: String, pos: Position, macroExpansion: String, macroExpandee: String) {
    val marker2expand = editorInput.asInstanceOf[FileEditorInput].getFile.createMarker(markerId)
    marker2expand.setAttribute(IMarker.CHAR_START, pos.offset)
    marker2expand.setAttribute(IMarker.CHAR_END, pos.offset + pos.length)
    marker2expand.setAttribute("macroExpandee", macroExpandee)
    marker2expand.setAttribute("macroExpansion", macroExpansion)
  }
}

trait MacroExpansionIndent {
  def document: IDocument
  def indentMacroExpansion(line: Int, macroExpansion: String) = {
    val lineRegion = document.getLineInformation(line)
    val prefix = document.get(lineRegion.getOffset, lineRegion.getLength).takeWhile(_.isWhitespace)
    val splittedMacroExpansion = macroExpansion.split(TextUtilities.getDefaultLineDelimiter(document))
    (splittedMacroExpansion.head +:
      splittedMacroExpansion.tail.map(prefix + _)).mkString("\n")
  }
}

trait PreserveDirtyState extends HasLogger {
  def textEditor: ScalaSourceFileEditor
  def editorInput: IEditorInput
  def document: IDocument

  private def selectionProvider = textEditor.getSelectionProvider

  private lazy val fTextFileBuffer = try {
    import org.eclipse.ui.editors.text.TextFileDocumentProvider
    import org.eclipse.ui.texteditor.AbstractDecoratedTextEditor
    val documentProvier = textEditor.getDocumentProvider().asInstanceOf[TextFileDocumentProvider]
    val documentProviderClass = Class.forName("org.eclipse.ui.editors.text.TextFileDocumentProvider")
    val fFileInfoMapField = documentProviderClass.getDeclaredField("fFileInfoMap")
    fFileInfoMapField.setAccessible(true)
    val fileInfoMap = fFileInfoMapField.get(documentProvier).asInstanceOf[java.util.Map[Any, Any]]

    val fileInfo = fileInfoMap.get(editorInput).asInstanceOf[TextFileDocumentProvider.FileInfo]

    Some(fileInfo.fTextFileBuffer)
  } catch {
    case e: ClassNotFoundException => //TODO: remove?
      eclipseLog.error(e.getStackTrace)
      None
    case e: ClassCastException => //TODO: remove?
      eclipseLog.error(e.getStackTrace)
      None
    case e: Throwable =>
      throw e
  }

  private class MacroPreserveSelectionChangeListener(val previousSelection: ISelection) extends ISelectionChangedListener {
    override def selectionChanged(event: SelectionChangedEvent) {
      selectionProvider.removeSelectionChangedListener(this)
      selectionProvider.setSelection(previousSelection)
    }
  }

  def replaceWithoutDirtyState(offset: Int, length: Int, text: String) {
    if (fTextFileBuffer.isDefined) {
      val buffer = fTextFileBuffer.get

      val previousDirtyState = buffer.isDirty
      val previousSelection = selectionProvider.getSelection
      selectionProvider.addSelectionChangedListener(new MacroPreserveSelectionChangeListener(previousSelection))
      textEditor.macroReplaceStart(previousDirtyState)
      document.replace(offset, length, text)
      buffer.setDirty(previousDirtyState)
      textEditor.macroReplaceEnd()
    } else {
      eclipseLog.error("fTextFileBuffer is not defined")
      document.replace(offset, length, text)
    }
  }
}