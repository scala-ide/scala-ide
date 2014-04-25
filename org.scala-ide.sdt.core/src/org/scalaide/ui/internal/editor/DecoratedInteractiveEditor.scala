package org.scalaide.ui.internal.editor

import org.eclipse.jface.text.source.Annotation
import org.eclipse.jface.text.source.IAnnotationModel
import org.eclipse.jface.text.source.IAnnotationModelExtension
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitDocumentProvider.ProblemAnnotation
import scala.collection.breakOut
import org.eclipse.jdt.core.compiler.IProblem
import org.scalaide.util.internal.eclipse.SWTUtils
import org.scalaide.util.internal.eclipse.AnnotationUtils._
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.ITextViewerExtension2
import org.eclipse.jdt.core.ICompilationUnit
import org.scalaide.util.internal.ui.DisplayThread

trait DecoratedInteractiveEditor extends ISourceViewerEditor {

  /** Return the annotation model associated with the current document. */
  private def annotationModel = getDocumentProvider.getAnnotationModel(getEditorInput).asInstanceOf[IAnnotationModel]

  private var previousAnnotations = List[Annotation]()

  /**
   * Update annotations on the editor from a list of IProblems
   */
  def updateErrorAnnotations(errors: List[IProblem], cu: ICompilationUnit) {
    val newAnnotations: Map[Annotation, Position] = (for (e <- errors) yield {
      val annotation = new ProblemAnnotation(e, cu) // no compilation unit
      val position = new Position(e.getSourceStart, e.getSourceEnd - e.getSourceStart + 1)
      (annotation, position)
    })(breakOut)

    annotationModel.replaceAnnotations(previousAnnotations, newAnnotations)
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

        DisplayThread.asyncExec { presViewer.asInstanceOf[ITextViewerExtension2].invalidateTextPresentation(taintedBounds._1, taintedLength) }
    } else {
        DisplayThread.asyncExec { getViewer.invalidateTextPresentation() }
    }
  }

}
