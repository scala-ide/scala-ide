package org.scalaide.util.internal.eclipse

import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.ITextSelection
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.Region
import org.eclipse.jface.text.source.Annotation
import org.eclipse.jface.text.source.IAnnotationModelExtension2
import org.eclipse.ui.IEditorPart
import org.eclipse.ui.texteditor.ITextEditor
import org.scalaide.core.compiler.InteractiveCompilationUnit
import org.scalaide.ui.internal.editor.InteractiveCompilationUnitEditor

// FIXME: This should be merged with [[org.scalaide.refactoring.internal.EditorHelpers]]
object EditorUtils {

  def openEditorAndApply[T](element: IJavaElement)(editor: IEditorPart => T): T =
    editor(JavaUI.openInEditor(element))

  /** Return the compilation unit open in the given editor.*/
  def getEditorCompilationUnit(editor: ITextEditor): Option[InteractiveCompilationUnit] = {
    editor match {
      case icuEditor: InteractiveCompilationUnitEditor =>
        Some(icuEditor.getInteractiveCompilationUnit)
      case _ =>
        None
    }
  }

  def getAnnotationsAtOffset(part: org.eclipse.ui.IEditorPart, offset: Int): Iterator[(Annotation, Position)] = {
    import scala.collection.JavaConverters._
    val model = JavaUI.getDocumentProvider.getAnnotationModel(part.getEditorInput)

    val annotations = model match {
      case null                            => Iterator.empty
      case am2: IAnnotationModelExtension2 => am2.getAnnotationIterator(offset, 1, true, true).asScala
      case _                               => model.getAnnotationIterator.asScala
    }

    val annotationsWithPositions = annotations collect {
      case ann: Annotation => (ann, model.getPosition(ann))
    }

    val annotationsAtOffset = annotationsWithPositions filter {
      case (_, pos) => pos.includes(offset)
    }

    annotationsAtOffset
  }

  def textSelection2region(selection: ITextSelection): IRegion =
    new Region(selection.getOffset, selection.getLength)

  def getTextSelection(editor: ITextEditor): Option[ITextSelection] = {
    import org.scalaide.util.internal.Utils._
    for {
      workbenchSite <- Option(editor.getSite)
      provider <- Option(workbenchSite.getSelectionProvider)
      selection <- Option(provider.getSelection)
      textSelection <- selection.asInstanceOfOpt[ITextSelection]
    } yield textSelection
  }
}
