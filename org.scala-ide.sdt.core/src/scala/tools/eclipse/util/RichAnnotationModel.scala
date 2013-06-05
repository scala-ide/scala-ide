package scala.tools.eclipse.util

import org.eclipse.jface.text.source.AnnotationModel
import org.eclipse.jface.text.source.Annotation
import org.eclipse.jface.text.source.IAnnotationModel
import org.eclipse.jface.text.source.IAnnotationModelExtension
import org.eclipse.jface.text.Position
import scala.collection.JavaConversions._
import org.eclipse.jface.text.ISynchronizable

object RichAnnotationModel {

  implicit def annotationModel2RichAnnotationModel(annotationModel: IAnnotationModel): RichAnnotationModel =
    new RichAnnotationModel(annotationModel)

}

class RichAnnotationModel(annotationModel: IAnnotationModel) {

  def withLock[T](f: => T): T = annotationModel match {
    case synchronizable: ISynchronizable =>
      synchronizable.getLockObject.synchronized { f }
    case _ =>
      f
  }

  def getAnnotations: List[Annotation] =
    annotationModel.getAnnotationIterator collect { case ann: Annotation => ann } toList

  def replaceAnnotations(annotations: Iterable[Annotation], replacements: Map[Annotation, Position]) {
    annotationModel.asInstanceOf[IAnnotationModelExtension].replaceAnnotations(annotations.toArray, replacements)
  }

  def deleteAnnotations(annotations: Iterable[Annotation]) {
    replaceAnnotations(annotations, Map())
  }

}
