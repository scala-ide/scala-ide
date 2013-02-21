package scala.tools.eclipse.ui

import org.eclipse.jface.text.source.IAnnotationModel
import org.eclipse.jface.text.source.IAnnotationModelExtension
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitDocumentProvider.ProblemAnnotation
import org.eclipse.jdt.core.compiler.IProblem
import scala.collection.breakOut
import scala.collection.JavaConverters.mapAsJavaMapConverter
import scala.tools.eclipse.util.SWTUtils
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.ITextViewerExtension2
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

    val newMap = newAnnotations.asJava
    annotationModel.replaceAnnotations(previousAnnotations.toArray, newMap)
    previousAnnotations = newAnnotations.keys.toList

    // This shouldn't be necessary in @dragos' opinion. But see #84 and
    // http://stackoverflow.com/questions/12507620/race-conditions-in-annotationmodel-error-annotations-lost-in-reconciler
    val presViewer = getViewer
    if (presViewer.isInstanceOf[ITextViewerExtension2]) {
        // TODO: This should be replaced by a better modularization of semantic highlighting PositionsChange
        val newPositions = newAnnotations.values
                def end (x:Position) = x.offset + x.length - 1
                val taintedBounds : (Int, Int) = ((Int.MaxValue, 0) /: newPositions) {(acc, p1) => (Math.min(acc._1, p1.offset), Math.max(acc._2, end(p1)))}
        val taintedLength = (taintedBounds._2 - taintedBounds._1 +1)

        SWTUtils.asyncExec { presViewer.asInstanceOf[ITextViewerExtension2].invalidateTextPresentation(taintedBounds._1, taintedLength) }
    } else {
        SWTUtils.asyncExec { getViewer.invalidateTextPresentation() }
    }
  }

}
