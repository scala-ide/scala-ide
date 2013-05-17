/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import java.lang.reflect.Constructor

import org.eclipse.jdt.core.{ ICompilationUnit, IImportContainer }
import org.eclipse.jdt.internal.core.{ CompilationUnit, ImportContainer, ImportDeclaration, JavaElement, JavaElementInfo, SourceType, SourceRefElement }

import scala.tools.eclipse.util.ReflectionUtils

object JavaElementFactory extends ReflectionUtils {
  private val stCtor = getDeclaredConstructor(classOf[SourceType], classOf[JavaElement], classOf[String])
  private val icCtor = getDeclaredConstructor(classOf[ImportContainer], classOf[CompilationUnit])
  private val idCtor = getDeclaredConstructor(classOf[ImportDeclaration], classOf[ImportContainer], classOf[String], classOf[Boolean])
  private val parentField = getDeclaredField(classOf[JavaElement], "parent")
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

  def createSourceRefElementInfo : JavaElementInfo =
    sreiCtor.newInstance().asInstanceOf[JavaElementInfo]

  def createSourceType(parent : JavaElement, name : String) =
    stCtor.newInstance(parent, name).asInstanceOf[SourceType]

  def createPackageDeclaration(cu : CompilationUnit, name : String) =
    pdCtor.newInstance(cu, name).asInstanceOf[SourceRefElement]

  def createImportContainer(parent : JavaElement) = {
    parent match {
      case cu : CompilationUnit =>
        icCtor.newInstance(parent).asInstanceOf[ImportContainer]
      case _ =>
        val ic = icCtor.newInstance(null).asInstanceOf[ImportContainer]
        setParent(ic, parent)
        ic
    }
  }

  def createImportDeclaration(parent : IImportContainer, name : String, isWildcard : Boolean) =
    idCtor.newInstance(parent, name, boolean2Boolean(isWildcard)).asInstanceOf[ImportDeclaration]

  def setParent(child : JavaElement, parent : JavaElement) {
    parentField.set(child, parent)
  }
}
