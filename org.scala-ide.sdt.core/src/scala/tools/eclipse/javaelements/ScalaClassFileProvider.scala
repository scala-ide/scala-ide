/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse
package javaelements

import java.io.ByteArrayInputStream
import org.eclipse.jdt.internal.core.{ ClassFile, PackageFragment }
import scala.tools.eclipse.contribution.weaving.jdt.cfprovider.IClassFileProvider
import scala.tools.eclipse.ScalaClassFileDescriber
import org.eclipse.jdt.core.IClassFile
import scala.tools.eclipse.util.HasLogger

class ScalaClassFileProvider extends IClassFileProvider with HasLogger {
  override def create(contents : Array[Byte], parent : PackageFragment, name : String) : ClassFile =
    ScalaClassFileDescriber.isScala(new ByteArrayInputStream(contents)) match {
      case Some(sourceFile) =>
        val scf = new ScalaClassFile(parent, name, sourceFile)
        val sourceMapper = parent.getSourceMapper
        if (sourceMapper == null)
          null
        else {
          val source = sourceMapper.findSource(scf.getType, sourceFile)
          if (source != null) scf else null
        }
      case _ => null
    }
  
  override def isInteresting(classFile: IClassFile): Boolean = {
    val res = ScalaPlugin.plugin.isScalaProject(classFile.getJavaProject())
    if (!res) 
      logger.debug("Not interested in %s in project %s".format(classFile.getElementName(), classFile.getJavaProject().getElementName()))
    res
  }
}
