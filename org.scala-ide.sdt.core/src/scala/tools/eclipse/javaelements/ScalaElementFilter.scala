/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import org.eclipse.jface.viewers.{ Viewer, ViewerFilter }

class ScalaElementFilter extends ViewerFilter {
	def select(viewer : Viewer, parentElement : AnyRef, element : AnyRef) : Boolean =
    !element.isInstanceOf[ScalaElement] || element.asInstanceOf[ScalaElement].isVisible
}
