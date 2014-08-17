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
  protected[macros] def editorInput: IEditorInput
  def createMacroMarker(markerId: String, pos: Position, macroExpansion: String, macroExpandee: String) {
    val marker2expand = editorInput.asInstanceOf[FileEditorInput].getFile.createMarker(markerId)
    marker2expand.setAttribute(IMarker.CHAR_START, pos.offset)
    marker2expand.setAttribute(IMarker.CHAR_END, pos.offset + pos.length)
    marker2expand.setAttribute("macroExpandee", macroExpandee)
    marker2expand.setAttribute("macroExpansion", macroExpansion)
  }
}

trait MacroExpansionIndent {
  protected[macros] def document: IDocument
  /*
   * Gets white characters on the expanded line,
   * add them to each line of macro expansion
   * */
  def indentMacroExpansion(line: Int, macroExpansion: String) = {
    val lineRegion = document.getLineInformation(line)
    val prefix = document.get(lineRegion.getOffset, lineRegion.getLength).takeWhile(_.isWhitespace)
    val splittedMacroExpansion = macroExpansion.split(TextUtilities.getDefaultLineDelimiter(document))
    (splittedMacroExpansion.head +:
      splittedMacroExpansion.tail.map(prefix + _)).mkString(TextUtilities.getDefaultLineDelimiter(document))
  }
}

/*
 * Contains replaceWithoutDirtyState function. The intent of this function is:
 * 1) to preserve the dirty state in the state it was before expanding the macro
 * 2) not to select the macro, when expanding
 * */
trait PreserveDirtyState extends HasLogger {
  protected[macros] def textEditor: ScalaSourceFileEditor
  protected[macros] def editorInput: IEditorInput
  protected[macros] def document: IDocument

  private def selectionProvider = textEditor.getSelectionProvider


  //Used to preserve modified state in the state it was before modifying the document.
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
    case e: Throwable => 
      eclipseLog.error("error: ",e)
      None
  }

  // Used for not selecting the expanded macro
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
      document.replace(offset, length, text)
    }
  }
}