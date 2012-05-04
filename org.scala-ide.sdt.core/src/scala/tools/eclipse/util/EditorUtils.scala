package scala.tools.eclipse.util

import org.eclipse.jdt.internal.core.JavaElement
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.source.Annotation
import org.eclipse.ui.IEditorPart
import scala.collection.JavaConverters._
import org.eclipse.jface.text.source.IAnnotationModelExtension2
import scala.tools.eclipse.ScalaWordFinder
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.ITextSelection

object EditorUtils {

  def openEditorAndApply[T](element: JavaElement)(editor: IEditorPart => T): T =
    editor(org.eclipse.jdt.ui.JavaUI.openInEditor(element))

  def getAnnotationsAtOffset(part: org.eclipse.ui.IEditorPart, offset: Int): Iterator[(Annotation, Position)] = {
    val model = org.eclipse.jdt.ui.JavaUI.getDocumentProvider.getAnnotationModel(part.getEditorInput)

    val annotations = model match {
      case am2: IAnnotationModelExtension2 => am2.getAnnotationIterator(offset, 1, true, true)
      case _ => model.getAnnotationIterator
    }

    val annotationsWithPositions = annotations.asScala collect {
      case ann: Annotation => (ann, model.getPosition(ann))
    }

    val annotationsAtOffset = annotationsWithPositions filter {
      case (_, pos) => pos.includes(offset)
    }

    annotationsAtOffset
  }

  def textSelection2region(selection: ITextSelection): IRegion = new IRegion {
    def getOffset = selection.getOffset
    def getLength = selection.getLength
  }
}