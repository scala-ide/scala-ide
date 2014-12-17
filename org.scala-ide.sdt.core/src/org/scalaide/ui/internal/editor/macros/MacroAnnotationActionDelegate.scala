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
import org.scalaide.logging.HasLogger
import org.eclipse.jface.text.source.AnnotationModel
import org.eclipse.jface.text.IRegion
import org.scalaide.ui.internal.editor.ScalaSourceFileEditor
import org.scalaide.ui.internal.editor.decorators.macros.ScalaMacroMarker
import org.scalaide.ui.internal.editor.decorators.macros.MacroExpansionAnnotation
import org.eclipse.jface.text.source.IAnnotationModelExtension2
import org.eclipse.jface.text.source.IAnnotationModel
import org.eclipse.jface.text.Position

/*
 * Creates MacroRulerAction and triggers MacroRulerAction.run() when clicking on gutter
 * macro expand/collapse annotations
 * */
class MacroAnnotationActionDelegate extends AbstractRulerActionDelegate with HasLogger {
  import org.eclipse.jface.action.Action
  import org.eclipse.jface.action.IAction
  import org.eclipse.ui.texteditor.TextEditorAction

  var macroRulerAction: Option[MacroRulerAction] = None

  /*
   * Expands and collapses macros, when clicking on gutter expand/collapse annotations
   * */
  class MacroRulerAction(val iTextEditor: ITextEditor, val iVerticalRulerInfo: IVerticalRulerInfo) extends Action with MacroCreateMarker with PreserveDirtyState with MacroExpansionIndent {
    override val editorInput = iTextEditor.getEditorInput
    // asInstance is for getAnnotationIterator(int offset, int length, ...) method
    val annotationModel = iTextEditor.getDocumentProvider.getAnnotationModel(editorInput).asInstanceOf[IAnnotationModelExtension2 with IAnnotationModel]
    override val document = iTextEditor.getDocumentProvider.getDocument(editorInput)

    override def textEditor = iTextEditor.asInstanceOf[ScalaSourceFileEditor]

    private def findAnnotationsStartOnLine(line: Int, annotationType: String) = {
      val iRegion = document.getLineInformation(line)
      val annotations = annotationModel.getAnnotationIterator(iRegion.getOffset, iRegion.getLength, /*canStartBefore*/ true, /*canEndAfter*/ true).toList.map(_.asInstanceOf[Annotation])
      val annotationIterator = for {
        annotation <- annotations
        if annotation.getType == annotationType
        pos = annotationModel.getPosition(annotation)
        if document.getLineOfOffset(pos.offset) == line
      } yield annotation
      annotationIterator.toList
    }

    private def expandMacroAnnotation(annotation: Annotation, line: Int) = {
      if (!annotation.isMarkedDeleted) {
          val pos = annotationModel.getPosition(annotation)

          val macroExpandee = document.get(pos.offset, pos.length)
          val macroExpansion = annotation.getText

          val indentedMacroExpansion = indentMacroExpansion(line, macroExpansion)

          replaceWithoutDirtyState(pos.offset, pos.length, indentedMacroExpansion)

          //create new if previously there were no annotations
          val annotations = annotationModel.getAnnotationIterator(pos.offset, pos.length, /*canStartBefore*/ true, /*canEndAfter*/ true).toList.map(_.asInstanceOf[Annotation])
          if (!annotations.exists(_.getType == ScalaMacroMarker.ID)) {
            editorInput match {
              case fileEditorInput: FileEditorInput =>
                val macroExpansionPosition = new Position(pos.offset, indentedMacroExpansion.length)
                createMacroMarker(ScalaMacroMarker.ID, pos, indentedMacroExpansion, macroExpandee)
              case e: Throwable =>
                eclipseLog.error("error:", e)
            }
          }

          annotationModel.removeAnnotation(annotation)
        }
    }

    private def collapseMacroAnnotation(annotation: Annotation) {
          val pos = annotationModel.getPosition(annotation)

          val marker = annotation.asInstanceOf[MarkerAnnotation].getMarker // ScalaMacroMarker.ID is marker
          val macroExpandee = marker.getAttribute("macroExpandee", "")

          replaceWithoutDirtyState(pos.offset, pos.length, macroExpandee)

          annotationModel.removeAnnotation(annotation)
        }

    override def run {
      val line = iVerticalRulerInfo.getLineOfLastMouseButtonActivity
      val macroAnnotations2Expand = findAnnotationsStartOnLine(line, MacroExpansionAnnotation.ID)

      macroAnnotations2Expand.foreach(annotation => expandMacroAnnotation(annotation, line))

      if (macroAnnotations2Expand.isEmpty) {
        val annotations2Collapse = findAnnotationsStartOnLine(line, ScalaMacroMarker.ID)

        annotations2Collapse.foreach(annotation => collapseMacroAnnotation(annotation))
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
    try{
      val t = new MacroRulerAction(editor, rulerInfo)
      macroRulerAction = Some(t)
      t
    }
    catch{
      case e: Throwable =>
        eclipseLog.error("error:",e)
        null
    }
  }
}