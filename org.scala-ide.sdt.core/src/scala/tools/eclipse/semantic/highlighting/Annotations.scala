package scala.tools.eclipse
package semantic.highlighting

import org.eclipse.jface.text.source.{ IAnnotationModelExtension, Annotation, IAnnotationModel, ISourceViewer }
import org.eclipse.jface.text.ISynchronizable

object AnnotationsTypes {
  final val Implicits = "scala.tools.eclipse.semantic.highlighting.implicitConversionsOrArgsAnnotation"
}

object Annotations {

  def update(sourceViewer: ISourceViewer, annotationType: String, toAdds: java.util.Map[Annotation, org.eclipse.jface.text.Position]): Unit = {
    update(sourceViewer.getAnnotationModel, annotationType, toAdds)
  }

  def update(model: IAnnotationModel, annotationType: String, toAdds: java.util.Map[Annotation, org.eclipse.jface.text.Position]): Unit = {
    if (model ne null) {
      model.asInstanceOf[ISynchronizable].getLockObject() synchronized {
        import scala.collection.JavaConversions._
        val annotations = model.getAnnotationIterator() collect { case ann: Annotation => ann }
        val toRemove = annotations.filter(annotationType == _.getType)
        val am = model.asInstanceOf[IAnnotationModelExtension]
        am.replaceAnnotations(toRemove.toArray, toAdds)
      }
    }
  }
}
