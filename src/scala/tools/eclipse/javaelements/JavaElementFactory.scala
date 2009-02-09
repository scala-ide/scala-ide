/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import java.lang.reflect.Constructor

import org.eclipse.jdt.internal.core.{ CompilationUnit, JavaElementInfo, SourceRefElement }

import lampion.util.ReflectionUtils

object JavaElementFactory extends ReflectionUtils {
  
  private val (sreiCtor, pdCtor) =
    privileged {
      val sreiCtor0 = 
        Class.forName("org.eclipse.jdt.internal.core.SourceRefElementInfo").
          getDeclaredConstructor().asInstanceOf[Constructor[AnyRef]]
      val pdCtor0 =
        Class.forName("org.eclipse.jdt.internal.core.PackageDeclaration").
          getDeclaredConstructor(classOf[CompilationUnit], classOf[String]).asInstanceOf[Constructor[AnyRef]]
  
      sreiCtor0.setAccessible(true)
      pdCtor0.setAccessible(true)
      
      (sreiCtor0, pdCtor0)
    }
  
  def createSourceRefElementInfo : JavaElementInfo = sreiCtor.newInstance().asInstanceOf[JavaElementInfo]
  
  def createPackageDeclaration(cu : CompilationUnit, name : String) =
    pdCtor.newInstance(cu, name).asInstanceOf[SourceRefElement]
}
