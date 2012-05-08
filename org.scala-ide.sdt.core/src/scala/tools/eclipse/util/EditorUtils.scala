package scala.tools.eclipse.util

import scala.collection.JavaConverters.asScalaIteratorConverter

import org.eclipse.jdt.internal.core.JavaElement
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.ITextSelection
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.Region
import org.eclipse.jface.text.source.Annotation
import org.eclipse.jface.text.source.IAnnotationModelExtension2
import org.eclipse.ui.IEditorPart

// FIXME: This should be merged with merged with {{{scala.tools.eclipse.refactoring.EditorHelpers}}}
object EditorUtils {

  def openEditorAndApply[T](element: JavaElement)(editor: IEditorPart => T): T =
    editor(org.eclipse.jdt.ui.JavaUI.openInEditor(element))

  def getAnnotationsAtOffset(part: org.eclipse.ui.IEditorPart, offset: Int): Iterator[(Annotation, Position)] = {
    val model = org.eclipse.jdt.ui.JavaUI.getDocumentProvider.getAnnotationModel(part.getEditorInput)

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
}