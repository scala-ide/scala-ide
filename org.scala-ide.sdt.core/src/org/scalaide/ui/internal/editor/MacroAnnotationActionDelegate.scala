package org.scalaide.ui.internal.editor

import org.eclipse.ui.texteditor.AbstractRulerActionDelegate
import org.eclipse.ui.texteditor.ITextEditor
import org.eclipse.jface.text.source.IVerticalRulerInfo
import org.eclipse.jface.text.source.Annotation
import collection.JavaConversions._
import org.eclipse.ui.part.FileEditorInput
import org.eclipse.core.resources.IMarker
import org.eclipse.ui.texteditor.MarkerAnnotation

class MacroAnnotationActionDelegate extends AbstractRulerActionDelegate {
  import org.eclipse.jface.action.Action
  import org.eclipse.jface.action.IAction
  import org.eclipse.ui.texteditor.TextEditorAction

  var macroRulerAction: Option[MacroRulerAction] = None  // Can be configured to work in mouseDown

  class MacroRulerAction(val iTextEditor: ITextEditor, val iVerticalRulerInfo: IVerticalRulerInfo) extends Action {
    private val editorInput = iTextEditor.getEditorInput
    private val annotationModel = iTextEditor.getDocumentProvider.getAnnotationModel(editorInput)
    private val document = iTextEditor.getDocumentProvider.getDocument(editorInput)

    private def findAnnotationsOnLine(line: Int, annotationType: String) = {
      val annotations = annotationModel.getAnnotationIterator.toList
      val annotationIterator = for {
        annotationNoType <- annotations
        annotation = annotationNoType.asInstanceOf[Annotation]
        if annotation.getType == annotationType
        pos = annotationModel.getPosition(annotation)
        if document.getLineOfOffset(pos.offset) == line
      } yield annotation
      annotationIterator.toList
    }

    override def run {
      import org.scalaide.ui.internal.editor.decorators.implicits.MacroExpansionAnnotation

      val line = iVerticalRulerInfo.getLineOfLastMouseButtonActivity
      val annotations2Expand = findAnnotationsOnLine(line, MacroExpansionAnnotation.ID)

      //If one clicked on expand annotation => expand macros on line. Macro expansion is in annotation text.
      annotations2Expand.foreach(annotation => if (!annotation.isMarkedDeleted) {
        val position = annotationModel.getPosition(annotation)
        val (pOffset, pLength) = (position.offset, position.length)
        val macroExpandee = document.get(pOffset, pLength)

        val macroExpansion = annotation.getText

        val macroLineStartPos = document.getLineOffset(document.getLineOfOffset(pOffset))
        val prefix = document.get(macroLineStartPos, pOffset - macroLineStartPos).takeWhile(_ == ' ')

        val splittedMacroExpansion = macroExpansion.split("\n")
        val indentedMacroExpansion = (splittedMacroExpansion.head +:
          splittedMacroExpansion.tail.map(prefix + _)).mkString("\n")

        //create new if previously there were no annotations
        val annotations = annotationModel.getAnnotationIterator.toList
        if (!annotations.map(_.asInstanceOf[Annotation]).exists(annotation => {
          val pos = annotationModel.getPosition(annotation)
          annotation.getType == "scala.tools.eclipse.macroMarkerId" &&
            pos.offset <= pOffset &&
            pOffset + pLength <= pos.offset + pos.length
        })) {
          val marker = editorInput.asInstanceOf[FileEditorInput].getFile.createMarker("scala.tools.eclipse.macroMarkerId")
          marker.setAttribute(IMarker.CHAR_START, pOffset)
          marker.setAttribute(IMarker.CHAR_END, pOffset + pLength)
          marker.setAttribute("macroExpandee", macroExpandee)
          marker.setAttribute("macroExpansion", indentedMacroExpansion)
        }

        annotationModel.removeAnnotation(annotation)
        document.replace(pOffset, pLength, indentedMacroExpansion)
      })

      if (annotations2Expand.isEmpty) {
        val annotations2Collapse = findAnnotationsOnLine(line, "scala.tools.eclipse.macroMarkerId")

        annotations2Collapse.foreach(annotation => {
          val position = annotationModel.getPosition(annotation)

          val marker = annotation.asInstanceOf[MarkerAnnotation].getMarker
          val macroExpandee = marker.getAttribute("macroExpandee").asInstanceOf[String]

          document.replace(position.offset, position.length, macroExpandee)

          marker.delete
          annotationModel.removeAnnotation(annotation)
        })
      }

      iTextEditor.asInstanceOf[ScalaMacroEditor].refreshMacroExpansionRegions()
    }
  }

  import org.eclipse.swt.events.MouseEvent
  override def mouseDown(mouseEventm: MouseEvent) {
    macroRulerAction.map(_.run())
  }
  def createAction(editor: ITextEditor, rulerInfo: IVerticalRulerInfo): IAction = {
    val t = new MacroRulerAction(editor, rulerInfo)
    macroRulerAction = Some(t)
    t
  }
}
