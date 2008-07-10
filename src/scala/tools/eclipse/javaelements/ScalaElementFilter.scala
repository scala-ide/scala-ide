/*
 * Copyright 2005-2008 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import org.eclipse.jface.viewers.{ Viewer, ViewerFilter }

class ScalaElementFilter extends ViewerFilter {
	def select(viewer : Viewer, parentElement : AnyRef, element : AnyRef) : Boolean = {
		if (!element.isInstanceOf[ScalaElement])
			true
    else
      element match {
        case d : ScalaDefElement if(d.getElementInfo.isInstanceOf[ScalaSourceConstructorInfo]) => false
        case mi : ScalaModuleInstanceElement => false
        case a : ScalaAccessorElement => false
        case _ => true
      }
	}
}
