package scala.tools.eclipse
package util

import org.eclipse.jface.text.source.{IAnnotationModelExtension, Annotation, IAnnotationModel, ISourceViewer}
import scala.tools.eclipse.internal.logging.Tracer

object AnnotationsTypes {
  val Implicits = "scala.tools.eclipse.ui.semantic.highlighting.implicitConversionsOrArgsAnnotation"
}

object Annotations {
  
  def update(sourceViewer : ISourceViewer, annotationType : String, toAdds : java.util.Map[Annotation, org.eclipse.jface.text.Position]) : Unit = {
    update(sourceViewer.getAnnotationModel, annotationType, toAdds)
  }
  
  def update(model : IAnnotationModel, annotationType : String, toAdds : java.util.Map[Annotation, org.eclipse.jface.text.Position]) : Unit = {
    if (model ne null) {
      Tracer.println("update annotations "+ annotationType+ "' : " + toAdds.size)
      var toRemove: List[Annotation] = Nil
      val it = model.getAnnotationIterator()
      while (it.hasNext) {
        val annot = it.next.asInstanceOf[Annotation]
        if (annotationType == annot.getType) {
          toRemove = annot :: toRemove
        }
      }
      val am = model.asInstanceOf[IAnnotationModelExtension]
      am.replaceAnnotations(toRemove.toArray, toAdds)
    }
  }
}

