package org.scalaide.ui.internal.jdt.model

import org.eclipse.jface.viewers.Viewer
import org.eclipse.jface.viewers.ViewerFilter
import org.scalaide.core.internal.jdt.model.ScalaElement

class ScalaElementFilter extends ViewerFilter {
  def select(viewer : Viewer, parentElement : AnyRef, element : AnyRef) : Boolean =
    !element.isInstanceOf[ScalaElement] || element.asInstanceOf[ScalaElement].isVisible
}
