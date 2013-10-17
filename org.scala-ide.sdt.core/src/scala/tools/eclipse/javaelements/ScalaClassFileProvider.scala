/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse
package javaelements

import java.io.ByteArrayInputStream
import org.eclipse.jdt.internal.core.ClassFile
import org.eclipse.jdt.internal.core.PackageFragment
import scala.tools.eclipse.contribution.weaving.jdt.cfprovider.IClassFileProvider
import scala.tools.eclipse.ScalaClassFileDescriber
import org.eclipse.jdt.core.IClassFile
import scala.tools.eclipse.logging.HasLogger

class ScalaClassFileProvider extends IClassFileProvider with HasLogger {
  override def create(contents : Array[Byte], parent : PackageFragment, name : String) : ClassFile =
    ScalaClassFileDescriber.isScala(new ByteArrayInputStream(contents)) match {
      case Some(sourcePath) => new ScalaClassFile(parent, name, sourcePath)
      case _ => null
    }

  override def isInteresting(classFile: IClassFile): Boolean = {
    val res = ScalaPlugin.plugin.isScalaProject(classFile.getJavaProject())
    if (!res)
      logger.debug("Not interested in %s in project %s".format(classFile.getElementName(), classFile.getJavaProject().getElementName()))
    res
  }
}
