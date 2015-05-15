package org.scalaide.core.internal.jdt.model

import java.lang.reflect.Constructor
import org.eclipse.jdt.core.IImportContainer
import org.eclipse.jdt.internal.core.CompilationUnit
import org.eclipse.jdt.internal.core.ImportContainer
import org.eclipse.jdt.internal.core.ImportDeclaration
import org.eclipse.jdt.internal.core.JavaElement
import org.eclipse.jdt.internal.core.SourceType
import org.eclipse.jdt.internal.core.SourceRefElement
import org.scalaide.util.internal.ReflectionUtils

object JavaElementFactory extends ReflectionUtils {
  private val stCtor = getDeclaredConstructor(classOf[SourceType], classOf[JavaElement], classOf[String])
  private val icCtor = getDeclaredConstructor(classOf[ImportContainer], classOf[CompilationUnit])
  private val idCtor = getDeclaredConstructor(classOf[ImportDeclaration], classOf[ImportContainer], classOf[String], classOf[Boolean])
  private val parentField = getDeclaredField(classOf[JavaElement], "parent")
  private val pdCtor = privileged {
    val pdCtor0 =
      Class.forName("org.eclipse.jdt.internal.core.PackageDeclaration").
        getDeclaredConstructor(classOf[CompilationUnit], classOf[String]).asInstanceOf[Constructor[AnyRef]]

    pdCtor0.setAccessible(true)
    pdCtor0
  }

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

  def setParent(child : JavaElement, parent : JavaElement): Unit = {
    parentField.set(child, parent)
  }
}
