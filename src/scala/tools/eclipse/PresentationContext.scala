/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.collection.mutable.{ LinkedHashMap, HashMap }

import org.eclipse.jface.text.Position
import org.eclipse.jface.text.source.projection.ProjectionAnnotation

class PresentationContext {
  val invalidate = new HashMap[Int,Int]
  var remove = List[ProjectionAnnotation]()
  var modified = List[ProjectionAnnotation]()
  val add = new LinkedHashMap[ProjectionAnnotation,Position]
}
