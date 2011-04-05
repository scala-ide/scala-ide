package scala.tools.eclipse.util

import org.eclipse.core.runtime.IAdaptable
object EclipseUtils {

  implicit def adaptableToPimpedAdaptable(adaptable: IAdaptable): PimpedAdaptable = new PimpedAdaptable(adaptable)

  class PimpedAdaptable(adaptable: IAdaptable) {

    def adaptTo[T](implicit m: Manifest[T]): T = adaptable.getAdapter(m.erasure).asInstanceOf[T]

    def adaptToSafe[T](implicit m: Manifest[T]): Option[T] = Option(adaptable.getAdapter(m.erasure).asInstanceOf[T])
    
  }

}