package scala.tools.eclipse.ui

import org.eclipse.jface.text.source.IAnnotationModel
import org.eclipse.jface.text.source.IAnnotationModelExtension
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitDocumentProvider.ProblemAnnotation
import org.eclipse.jdt.core.compiler.IProblem
import scala.collection.breakOut
import scala.collection.JavaConverters
import scala.tools.eclipse.util.SWTUtils
import org.eclipse.jface.text.Position
import scala.tools.eclipse.ISourceViewerEditor

trait DecoratedInteractiveEditor extends ISourceViewerEditor {

  type IAnnotationModelExtended = IAnnotationModel with IAnnotationModelExtension

  /** Return the annotation model associated with the current document. */
  private def annotationModel: IAnnotationModelExtended = getDocumentProvider.getAnnotationModel(getEditorInput).asInstanceOf[IAnnotationModelExtended]

  private var previousAnnotations = List[ProblemAnnotation]()

  /**
   * Update annotations on the editor from a list of IProblems
   */

  def updateErrorAnnotations(errors: List[IProblem]) {
    val newAnnotations: Map[ProblemAnnotation, Position] = (for (e <- errors) yield {
      val annotation = new ProblemAnnotation(e, null) // no compilation unit
      val position = new Position(e.getSourceStart, e.getSourceEnd - e.getSourceStart + 1)
      (annotation, position)
    })(breakOut)

    import JavaConverters._
    val newMap = newAnnotations.asJava
    annotationModel.replaceAnnotations(previousAnnotations.toArray, newMap)
    previousAnnotations = newAnnotations.keys.toList

    // This shouldn't be necessary in @dragos' opinion. But see #84 and
    // http://stackoverflow.com/questions/12507620/race-conditions-in-annotationmodel-error-annotations-lost-in-reconciler
    SWTUtils.asyncExec { getViewer.invalidateTextPresentation() }
  }

}
