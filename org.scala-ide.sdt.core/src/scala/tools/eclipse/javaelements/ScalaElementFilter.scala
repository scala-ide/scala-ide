package scala.tools.eclipse.javaelements

import org.eclipse.jface.viewers.Viewer
import org.eclipse.jface.viewers.ViewerFilter

class ScalaElementFilter extends ViewerFilter {
  def select(viewer : Viewer, parentElement : AnyRef, element : AnyRef) : Boolean =
    !element.isInstanceOf[ScalaElement] || element.asInstanceOf[ScalaElement].isVisible
}
