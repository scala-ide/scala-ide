package org.scalaide.util.internal.eclipse

import scala.collection.JavaConverters._

import org.eclipse.jface.text.ISynchronizable
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.source.Annotation
import org.eclipse.jface.text.source.IAnnotationModel
import org.eclipse.jface.text.source.IAnnotationModelExtension
import org.eclipse.jface.text.source.ISourceViewer

object AnnotationUtils {

  implicit class RichModel(annotationModel: IAnnotationModel) {

    def withLock[T](f: => T): T = annotationModel match {
      case synchronizable: ISynchronizable =>
        synchronizable.getLockObject.synchronized { f }
      case _ =>
        f
    }

    def getAnnotations: List[Annotation] = {
      val annotations = annotationModel.getAnnotationIterator.asScala collect { case ann: Annotation => ann }
      annotations.toList
    }

    def replaceAnnotations(annotations: Iterable[Annotation], replacements: Map[Annotation, Position]) {
      annotationModel.asInstanceOf[IAnnotationModelExtension].replaceAnnotations(annotations.toArray, replacements.asJava)
    }

    def deleteAnnotations(annotations: Iterable[Annotation]) {
      replaceAnnotations(annotations, Map())
    }

  }

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
