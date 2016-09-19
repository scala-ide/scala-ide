package org.scalaide.ui.editor

import scala.collection.JavaConverters._
import scala.collection.breakOut

import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitDocumentProvider.ProblemAnnotation
import org.eclipse.jface.text.ITextViewerExtension2
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.source.Annotation
import org.eclipse.jface.text.source.IAnnotationModelExtension2
import org.scalaide.util.internal.eclipse.AnnotationUtils._
import org.scalaide.util.ui.DisplayThread

trait DecoratedInteractiveEditor extends ISourceViewerEditor {

  /** Return the annotation model associated with the current document. */
  private def annotationModel = Option(getDocumentProvider).map(_.getAnnotationModel(getEditorInput))

  private var previousAnnotations = List[Annotation]()

  /**
   * This removes all annotations in the region between `start` and `end`.
   */
  def removeAnnotationsInRegion(start: Int, end: Int): Unit = annotationModel foreach { model ⇒
    val annsToRemove = model match {
      case model: IAnnotationModelExtension2 ⇒
        model.getAnnotationIterator(start, end - start, /*canStartBefore*/ false, /*canEndAfter*/ false).asScala
      case _ ⇒
        model.getAnnotationIterator.asScala.filter { ann ⇒
          val pos = model.getPosition(ann)
          pos.offset >= start && pos.offset + pos.length <= end
        }
    }
    model.deleteAnnotations(annsToRemove.toSeq)
  }

  /**
   * Update annotations on the editor from a list of IProblems
   */
  def updateErrorAnnotations(errors: List[IProblem], cu: ICompilationUnit): Unit = annotationModel foreach { model ⇒
    val newAnnotations: Map[Annotation, Position] = (for (e ← errors) yield {
      val annotation = new ProblemAnnotation(e, cu) // no compilation unit
      val position = new Position(e.getSourceStart, e.getSourceEnd - e.getSourceStart + 1)
      (annotation, position)
    })(breakOut)

    model.replaceAnnotations(previousAnnotations, newAnnotations)
    previousAnnotations = newAnnotations.keys.toList

    // This shouldn't be necessary in @dragos' opinion. But see #84 and
    // http://stackoverflow.com/questions/12507620/race-conditions-in-annotationmodel-error-annotations-lost-in-reconciler
    getViewer match {
      case viewer: ITextViewerExtension2 ⇒
        // TODO: This should be replaced by a better modularization of semantic highlighting PositionsChange
        val newPositions = newAnnotations.values
        def end(x: Position) = x.offset + x.length - 1
        val taintedBounds = (newPositions foldLeft (Int.MaxValue, 0)) { (acc, p1) ⇒ (Math.min(acc._1, p1.offset), Math.max(acc._2, end(p1))) }
        val taintedLength = (taintedBounds._2 - taintedBounds._1 + 1)

        DisplayThread.asyncExec {
          viewer.invalidateTextPresentation(taintedBounds._1, taintedLength)
        }
      case viewer ⇒
        DisplayThread.asyncExec {
          viewer.invalidateTextPresentation()
        }
    }
  }

}
