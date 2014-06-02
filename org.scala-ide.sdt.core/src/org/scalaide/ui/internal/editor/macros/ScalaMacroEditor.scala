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

class MacroLineRange(val startLine: Int, val endLine: Int) {}

trait ScalaMacroEditor extends PreserveDirtyState with MacroCreateMarker { self: ScalaSourceFileEditor =>
  var macroExpansionRegions: List[MacroLineRange] = Nil

  def editorInput = getEditorInput
  def document = getDocumentProvider.getDocument(editorInput)
  def annotationModel = getDocumentProvider.getAnnotationModel(editorInput)

  val textEditor = this

  def expandMacros() {
    val annotations = annotationModel.getAnnotationIterator.toList
    for {
      annotationNoType <- annotations
      annotation = annotationNoType.asInstanceOf[Annotation]
      if annotation.getType == Marker2Expand.ID
    } {
      val marker = annotationNoType.asInstanceOf[MarkerAnnotation].getMarker
      val pos = annotationModel.getPosition(annotation)
      val macroExpandee = marker.getAttribute(MacroNames.macroExpandee).asInstanceOf[String]
      val macroExpansion = marker.getAttribute(MacroNames.macroExpansion).asInstanceOf[String]

      createMacroMarker(ScalaMacroMarker.ID, pos, macroExpansion, macroExpandee)
      replaceWithoutDirtyState(pos.offset, pos.length, macroExpansion)

      annotationModel.removeAnnotation(annotation)
    }
    refreshMacroExpansionRegions()
  }

  def refreshMacroExpansionRegions() {
    val annotations = annotationModel.getAnnotationIterator.toList
    macroExpansionRegions = for {
      annotationNoType <- annotations
      annotation = annotationNoType.asInstanceOf[Annotation]
      if annotation.getType == ScalaMacroMarker.ID
      pos = annotationModel.getPosition(annotation)
    } yield new MacroLineRange(document.getLineOfOffset(pos.offset), document.getLineOfOffset(pos.offset + pos.length))
    lineNumberCorresponder.refreshLineNumbers()
  }

  def collapseMacros() {
    val annotations = for {
      annotationsNoType <- annotationModel.getAnnotationIterator.toList
      annotation = annotationsNoType.asInstanceOf[Annotation]
      if annotation.getType == ScalaMacroMarker.ID
    } {
      val pos = annotationModel.getPosition(annotation)
      val marker = annotation.asInstanceOf[MarkerAnnotation].getMarker
      val macroExpandee = marker.getAttribute(MacroNames.macroExpandee).asInstanceOf[String]
      val macroExpansion = document.get(pos.getOffset, pos.getLength)

      createMacroMarker(Marker2Expand.ID, pos, macroExpansion, macroExpandee)
      replaceWithoutDirtyState(pos.offset, pos.length, macroExpandee)

      annotationModel.removeAnnotation(annotation)
    }
    refreshMacroExpansionRegions()
  }
}
