package org.scalaide.ui.internal.editor

import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor
import org.eclipse.jface.text.source.IAnnotationModelListener
import org.eclipse.jface.text.source.IAnnotationModel
import scala.collection.JavaConversions._
import org.eclipse.jface.text.source.Annotation
import org.eclipse.ui.texteditor.MarkerAnnotation
import org.eclipse.ui.part.FileEditorInput
import org.eclipse.core.resources.IMarker
import org.scalaide.ui.internal.editor.decorators.implicits.MacroExpansionAnnotation

class MyRange(val startLine: Int, val endLine: Int) {}

trait ScalaMacroEditor_ { self: ScalaSourceFileEditor =>
  //TODO: createBaseExpansion
  //TODO: check multiple lines macros
  var macroExpansionRegions: List[MyRange] = Nil
  val macroAnnotationModelListener = new MacroAnnotationsModelListener

  def editorInput = getEditorInput
  def document = getDocumentProvider.getDocument(editorInput)
  def annotationModel = getDocumentProvider.getAnnotationModel(editorInput)

  def refreshMacroExpansionRegions(){
    val annotations = annotationModel.getAnnotationIterator.toList
    macroExpansionRegions = for{
      annotationNoType <- annotations
      annotation = annotationNoType.asInstanceOf[Annotation]
      if annotation.getType == "scala.tools.eclipse.macroMarkerId"
      pos = annotationModel.getPosition(annotation)
    } yield new MyRange(document.getLineOfOffset(pos.offset), document.getLineOfOffset(pos.offset + pos.length))
    lineNumberCorresponder.refreshLineNumbers()
  }

  def removeMacroExpansions() {
    val annotations = for {
      annotationsNoType <- annotationModel.getAnnotationIterator.toList
      annotation = annotationsNoType.asInstanceOf[Annotation]
      if annotation.getType == "scala.tools.eclipse.macroMarkerId"
    } yield annotation

    annotations.foreach(annotation => {
      val pos = annotationModel.getPosition(annotation)

      val marker = annotation.asInstanceOf[MarkerAnnotation].getMarker
      val macroExpandee = marker.getAttribute("macroExpandee").asInstanceOf[String]
      marker.delete
      annotationModel.removeAnnotation(annotation)

      val marker2expand = editorInput.asInstanceOf[FileEditorInput].getFile.createMarker("scala.tools.eclipse.macro2expand")
      marker2expand.setAttribute(IMarker.CHAR_START, pos.offset)
      marker2expand.setAttribute(IMarker.CHAR_END, pos.offset + pos.length)

      document.replace(pos.offset, pos.length, macroExpandee)
    })
  }

  class MacroAnnotationsModelListener extends IAnnotationModelListener {
    override def modelChanged(model: IAnnotationModel) {
      val annotations = annotationModel.getAnnotationIterator.toList
      for {
        annotationNoType <- annotations
        annotation = annotationNoType.asInstanceOf[Annotation]
        if annotation.getType == "scala.tools.eclipse.macro2expand"
        pos = annotationModel.getPosition(annotation)

        annotationNoType2 <- annotations
        annotation2 = annotationNoType2.asInstanceOf[Annotation]
        if annotation2.getType == MacroExpansionAnnotation.ID
        pos2 = annotationModel.getPosition(annotation2)

        if pos.offset == pos2.offset && pos.length == pos2.length
      } {
        val marker = annotation.asInstanceOf[MarkerAnnotation].getMarker
        val macroExpandee = document.get(pos.offset, pos.length)

        val macroExpansion = annotation2.getText

        val macroLineStartPos = document.getLineOffset(document.getLineOfOffset(pos.offset))
        val prefix = document.get(macroLineStartPos, pos.offset - macroLineStartPos).takeWhile(_ == ' ')

        val splittedMacroExpansion = macroExpansion.split("\n")
        val indentedMacroExpansion = (splittedMacroExpansion.head +:
          splittedMacroExpansion.tail.map(prefix + _)).mkString("\n")

        marker.delete
        annotationModel.removeAnnotation(annotation)
        annotationModel.removeAnnotation(annotation2)

        document.replace(pos.offset, pos.length, indentedMacroExpansion)

        val marker2 = editorInput.asInstanceOf[FileEditorInput].getFile.createMarker("scala.tools.eclipse.macroMarkerId")
        marker2.setAttribute(IMarker.CHAR_START, pos.offset)
        marker2.setAttribute(IMarker.CHAR_END, pos.offset + indentedMacroExpansion.length)
        marker2.setAttribute("macroExpandee", macroExpandee)
        marker2.setAttribute("macroExpansion", indentedMacroExpansion)
      }
    }
  }
}
