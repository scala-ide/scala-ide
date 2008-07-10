/*
 * Copyright 2005-2008 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import java.lang.reflect.Constructor

import org.eclipse.jdt.internal.core.{ CompilationUnit, SourceRefElement }
  
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
  
  def createSourceRefElementInfo : AnyRef = sreiCtor.newInstance().asInstanceOf[AnyRef]
  
  def createPackageDeclaration(cu : CompilationUnit, name : String) =
    pdCtor.newInstance(cu, name).asInstanceOf[SourceRefElement]
}
