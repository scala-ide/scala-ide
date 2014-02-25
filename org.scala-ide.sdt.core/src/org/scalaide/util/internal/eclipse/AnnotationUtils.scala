package org.scalaide.util.internal.eclipse

import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.jface.text.source.IAnnotationModel
import org.eclipse.jface.text.ISynchronizable
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.source.Annotation
import org.scalaide.util.internal.eclipse.RichAnnotationModel._

object AnnotationUtils {

  def update(sourceViewer: ISourceViewer, annotationType: String, newAnnotations: Map[Annotation, Position]) {
    for (annotationModel <- Option(sourceViewer.getAnnotationModel))
      update(annotationModel, annotationType, newAnnotations)
  }

  /**
   *  Replace annotations of the given annotationType with the given new annotations
   */
  private def update(model: IAnnotationModel, annotationType: String, newAnnotations: Map[Annotation, Position]) {
    model.withLock {
      val annotationsToRemove = model.getAnnotations.filter(_.getType == annotationType)
      model.replaceAnnotations(annotationsToRemove, newAnnotations)
    }
  }
}
