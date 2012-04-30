package scala.tools.eclipse.ui

import org.eclipse.ui.IEditorPart
import org.eclipse.jface.text.source.Annotation
import org.eclipse.jface.text.Position
import org.eclipse.jdt.internal.core.JavaElement

object EditorUtils {

  def withEditor[T](element: JavaElement)(editor: IEditorPart => T): T =
    editor(org.eclipse.jdt.ui.JavaUI.openInEditor(element))

  def getAnnotationsAtOffset(part: org.eclipse.ui.IEditorPart, offset: Int): List[(Annotation, Position)] = {
    val model = org.eclipse.jdt.ui.JavaUI.getDocumentProvider.getAnnotationModel(part.getEditorInput)
    import scala.collection.JavaConversions._

    val annotationsWithPositions = model.getAnnotationIterator collect {
      case ann: Annotation => (ann, model.getPosition(ann))
    }

    val annotationsAtOffset = annotationsWithPositions filter {
      case (_, pos) => pos.includes(offset)
    }
    annotationsAtOffset.toList
  }
}