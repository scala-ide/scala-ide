/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import java.io.ByteArrayInputStream

import org.eclipse.jdt.internal.core.{ ClassFile, PackageFragment }

import scala.tools.eclipse.contribution.weaving.jdt.cfprovider.IClassFileProvider

import scala.tools.eclipse.ScalaClassFileDescriber

class ScalaClassFileProvider extends IClassFileProvider {
  override def create(contents : Array[Byte], parent : PackageFragment, name : String) : ClassFile =
    ScalaClassFileDescriber.isScala(new ByteArrayInputStream(contents)) match {
      case Some(sourceFile) =>
        val scf = new ScalaClassFile(parent, name, sourceFile)
        val sourceMapper = parent.getSourceMapper      
        val source = sourceMapper.findSource(scf.getType0, sourceFile)
        if (source != null) scf else null
      case _ => null
    }
}
