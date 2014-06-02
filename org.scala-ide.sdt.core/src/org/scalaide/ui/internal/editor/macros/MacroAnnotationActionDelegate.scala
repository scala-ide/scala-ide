package org.scalaide.ui.internal.editor.macros

import org.eclipse.ui.texteditor.AbstractRulerActionDelegate
import org.eclipse.ui.texteditor.ITextEditor
import org.eclipse.jface.text.source.IVerticalRulerInfo
import org.eclipse.jface.text.source.Annotation
import collection.JavaConversions._
import org.eclipse.ui.part.FileEditorInput
import org.eclipse.core.resources.IMarker
import org.eclipse.ui.texteditor.MarkerAnnotation
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.jface.viewers.ISelection
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.jface.text.TextSelection
import org.eclipse.jface.text.TextUtilities
import org.scalaide.logging.Logger
import org.eclipse.core.runtime.ILog
import org.scalaide.core.ScalaPlugin
import org.scalaide.logging.HasLogger
import org.eclipse.jface.text.source.AnnotationModel
import org.scalaide.ui.internal.editor.macros.MacroCreateMarker
import org.scalaide.ui.internal.editor.macros.PreserveDirtyState
import org.eclipse.jface.text.IRegion
import org.scalaide.ui.internal.editor.macros.MacroExpansionIndent
import org.scalaide.ui.internal.editor.ScalaSourceFileEditor
import org.scalaide.ui.internal.editor.decorators.macros.ScalaMacroMarker
import org.scalaide.ui.internal.editor.decorators.macros.MacroExpansionAnnotation

class MacroAnnotationActionDelegate extends AbstractRulerActionDelegate with HasLogger  {
  import org.eclipse.jface.action.Action
  import org.eclipse.jface.action.IAction
  import org.eclipse.ui.texteditor.TextEditorAction

  var macroRulerAction: Option[MacroRulerAction] = None

  class MacroRulerAction(val iTextEditor: ITextEditor, val iVerticalRulerInfo: IVerticalRulerInfo) extends Action with MacroCreateMarker with PreserveDirtyState with MacroExpansionIndent {
    val editorInput = iTextEditor.getEditorInput
    // asInstance is for getAnnotationIterator(int offset, int length, ...) method
    val annotationModel = iTextEditor.getDocumentProvider.getAnnotationModel(editorInput).asInstanceOf[AnnotationModel]
    val document = iTextEditor.getDocumentProvider.getDocument(editorInput)

    def textEditor = iTextEditor.asInstanceOf[ScalaSourceFileEditor]

    private def findAnnotationsStartOnLine(line: Int, annotationType: String) = {
      val iRegion = document.getLineInformation(line)
      val annotations = annotationModel.getAnnotationIterator(iRegion.getOffset, iRegion.getLength, /*canStartBefore*/ true, /*canEndAfter*/ true).toList
      val annotationIterator = for {
        annotationNoType <- annotations
        annotation = annotationNoType.asInstanceOf[Annotation] // safe
        if annotation.getType == annotationType
        pos = annotationModel.getPosition(annotation)
        if document.getLineOfOffset(pos.offset) == line
      } yield annotation
      annotationIterator.toList
    }

    override def run {
      val line = iVerticalRulerInfo.getLineOfLastMouseButtonActivity
      val annotations2Expand = findAnnotationsStartOnLine(line, MacroExpansionAnnotation.ID)

      //If one clicked on expand annotation => expand macros on line. Macro expansion is in annotation text.
      annotations2Expand.foreach(annotation => if (!annotation.isMarkedDeleted) {
        val pos = annotationModel.getPosition(annotation)

        val macroExpandee = document.get(pos.offset, pos.length)
        val macroExpansion = annotation.getText

        val indentedMacroExpansion = indentMacroExpansion(line, macroExpansion)

        //create new if previously there were no annotations
        val annotations = annotationModel.getAnnotationIterator(pos.offset, pos.length, /*canStartBefore*/ true, /*canEndAfter*/ true).toList
        if (!annotations.map(_.asInstanceOf[Annotation]).exists(_.getType == ScalaMacroMarker.ID)) {
          editorInput match {
            case fileEditorInput: FileEditorInput =>
              createMacroMarker(ScalaMacroMarker.ID, pos, indentedMacroExpansion, macroExpandee)
            case _ =>
              eclipseLog.error("Wrong type for editorInput")
          }
        }

        replaceWithoutDirtyState(pos.offset, pos.length, indentedMacroExpansion)
        annotationModel.removeAnnotation(annotation)
      })

      if (annotations2Expand.isEmpty) {
        val annotations2Collapse = findAnnotationsStartOnLine(line, ScalaMacroMarker.ID)

        annotations2Collapse.foreach(annotation => {
          val pos = annotationModel.getPosition(annotation)

          val marker = annotation.asInstanceOf[MarkerAnnotation].getMarker // ScalaMacroMarker.ID is marker
          val macroExpandee = marker.getAttribute("macroExpandee").asInstanceOf[String] // macroExpandee is String

          replaceWithoutDirtyState(pos.offset, pos.length, macroExpandee)

          annotationModel.removeAnnotation(annotation)
        })
      }

      // MacroAnnotations are applyed only to Scala editor with ScalaMacroEditor
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