/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import org.eclipse.jdt.internal.core.{ ClassFile, PackageFragment }

class ScalaClassFile(parent : PackageFragment, name : String) extends ClassFile(parent, name) with ScalaElement with ImageSubstituter {
  override def replacementImage = ScalaImages.SCALA_CLASS_FILE
}
