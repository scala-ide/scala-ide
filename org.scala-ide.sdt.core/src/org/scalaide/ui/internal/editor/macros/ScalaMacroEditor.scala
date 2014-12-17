package org.scalaide.ui.internal.editor.macros

import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor
import org.eclipse.jface.text.source.IAnnotationModelListener
import org.eclipse.jface.text.source.IAnnotationModel
import scala.collection.JavaConversions._
import org.eclipse.jface.text.source.Annotation
import org.eclipse.ui.texteditor.MarkerAnnotation
import org.eclipse.ui.part.FileEditorInput
import org.eclipse.core.resources.IMarker
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.jface.viewers.ISelection
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.scalaide.ui.internal.editor.ScalaSourceFileEditor
import org.scalaide.ui.internal.editor.decorators.macros.Marker2Expand
import org.scalaide.ui.internal.editor.decorators.macros.MacroNames
import org.scalaide.ui.internal.editor.decorators.macros.ScalaMacroMarker
import org.eclipse.jface.text.Position

class MacroLineRange(val startLine: Int, val endLine: Int)

/**
 *  Contains macro helper functions.
 *  */
trait ScalaMacroEditor extends PreserveDirtyState with MacroCreateMarker with ScalaLineNumberMacroEditor { self: ScalaSourceFileEditor =>
  protected[macros] var macroExpansionRegions: List[MacroLineRange] = Nil

  protected[macros] def editorInput = getEditorInput
  protected[macros] def document = getDocumentProvider.getDocument(editorInput)
  protected[macros] def annotationModel = getDocumentProvider.getAnnotationModel(editorInput)
  private def annotations = annotationModel.getAnnotationIterator.toList.map(_.asInstanceOf[Annotation])

  protected[macros] val textEditor = this

  /* When applying macros dirty state should not change */
  var isDirtyState: Option[Boolean] = None
  def macroReplaceStart(dirtyState: Boolean) {
    isDirtyState = Some(dirtyState)
  }
  def macroReplaceEnd() {
    isDirtyState = None
  }

  def expandMacros() {
    for {
      annotation <- annotations
      if annotation.getType == Marker2Expand.ID
    } {
      val marker = annotation.asInstanceOf[MarkerAnnotation].getMarker
      val pos = annotationModel.getPosition(annotation)
      val macroExpandee = marker.getAttribute(MacroNames.macroExpandee, "")
      val macroExpansion = marker.getAttribute(MacroNames.macroExpansion, "")

      replaceWithoutDirtyState(pos.offset, pos.length, macroExpansion)
      val macroExpansionPosition = new Position(pos.offset, macroExpansion.length)
      createMacroMarker(ScalaMacroMarker.ID, macroExpansionPosition, macroExpansion, macroExpandee)

      annotationModel.removeAnnotation(annotation)
    }
    refreshMacroExpansionRegions()
  }

  def refreshMacroExpansionRegions() {
    macroExpansionRegions = for {
      annotation <- annotations
      if annotation.getType == ScalaMacroMarker.ID
    } yield {
      val pos = annotationModel.getPosition(annotation)
      new MacroLineRange(document.getLineOfOffset(pos.offset), document.getLineOfOffset(pos.offset + pos.length))
    }
    lineNumberCorresponder.refreshLineNumbers()
  }

  def collapseMacros() {
    for {
      annotation <- annotations
      if annotation.getType == ScalaMacroMarker.ID
    } {
      val pos = annotationModel.getPosition(annotation)
      val marker = annotation.asInstanceOf[MarkerAnnotation].getMarker
      val macroExpandee = marker.getAttribute(MacroNames.macroExpandee, "")
      val macroExpansion = document.get(pos.getOffset, pos.getLength)

      createMacroMarker(Marker2Expand.ID, pos, macroExpansion, macroExpandee)
      replaceWithoutDirtyState(pos.offset, pos.length, macroExpandee)

      annotationModel.removeAnnotation(annotation)
    }
    refreshMacroExpansionRegions()
  }
}
