/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.launching

import org.eclipse.jdt.internal.launching.JavaSourceLookupDirector

class ScalaSourceLookupDirector extends JavaSourceLookupDirector {
  override def findSourceElements(o : AnyRef) : Array[AnyRef] = o match {
    case path : String if path endsWith ".java" => {
      val elements = super.findSourceElements(path.take(path.length-5)+".scala")
      if (elements == null)
        super.findSourceElements(o)
      else
        elements
    }
    case _ => super.findSourceElements(o) 
  }
}
