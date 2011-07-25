package scala.tools.eclipse
package semantic.highlighting

import org.eclipse.jface.text.source.{ IAnnotationModelExtension, Annotation, IAnnotationModel, ISourceViewer }
import org.eclipse.jface.text.ISynchronizable

object AnnotationsTypes {
  val Implicits = "scala.tools.eclipse.semantic.highlighting.implicitConversionsOrArgsAnnotation"
}

object Annotations {

  def update(sourceViewer: ISourceViewer, annotationType: String, toAdds: java.util.Map[Annotation, org.eclipse.jface.text.Position]): Unit = {
    update(sourceViewer.getAnnotationModel, annotationType, toAdds)
  }

  def update(model: IAnnotationModel, annotationType: String, toAdds: java.util.Map[Annotation, org.eclipse.jface.text.Position]): Unit = {
    if (model ne null) {
      model.asInstanceOf[ISynchronizable].getLockObject() synchronized {
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
}

