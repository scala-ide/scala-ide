/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import scala.collection.immutable.Seq
import scala.util.NameTransformer

import org.eclipse.jdt.core.{ IField, IJavaElement, IMember, IMethod, IType }
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants
import org.eclipse.jdt.internal.core.{
  BinaryType, JavaElement, JavaElementInfo, LocalVariable, SourceConstructorInfo, SourceField, SourceFieldElementInfo,
  SourceMethod, SourceMethodElementInfo, SourceMethodInfo, SourceType, SourceTypeElementInfo } 
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.internal.ui.viewsupport.{ JavaElementImageProvider }
import org.eclipse.jdt.ui.JavaElementImageDescriptor
import org.eclipse.jface.resource.ImageDescriptor
import org.eclipse.swt.graphics.Image

import scala.tools.eclipse.contribution.weaving.jdt.IScalaElement
import scala.tools.eclipse.contribution.weaving.jdt.ui.IMethodOverrideInfo
import scala.tools.eclipse.util.ReflectionUtils

trait ScalaElement extends JavaElement with IScalaElement {
  def getElementInfo : AnyRef
  def getElementName : String
  def scalaName : String = getElementName
  def labelName : String = scalaName
  def getLabelText(flags : Long) : String = labelName
  def getImageDescriptor : ImageDescriptor = null
  def isVisible = true
  
  override def getCompilationUnit() = {
    val cu = super.getCompilationUnit()
    if (cu != null) cu else new CompilationUnitAdapter(getClassFile().asInstanceOf[ScalaClassFile])
  }
    
  override def getAncestor(ancestorType : Int) : IJavaElement = {
    val ancestor = super.getAncestor(ancestorType)
    if (ancestor != null)
      ancestor
    else if(ancestorType == IJavaElement.COMPILATION_UNIT)
      new CompilationUnitAdapter(getClassFile().asInstanceOf[ScalaClassFile])
    else
      null
  }
}

trait ScalaFieldElement extends ScalaElement

class ScalaSourceTypeElement(parent : JavaElement, name : String)
  extends SourceType(parent, name) with ScalaElement {

  def getCorrespondingElement(element : IJavaElement) : Option[IJavaElement] = {
    val name = element.getElementName
    val tpe = element.getElementType
    getChildren.find(e => e.getElementName == name && e.getElementType == tpe)
  }
  
  override def getType(typeName : String) : IType = {
    val tpe = super.getType(typeName)
    getCorrespondingElement(tpe).getOrElse(tpe).asInstanceOf[IType]
  }
  
  override def getField(fieldName : String) : IField = {
    val field = super.getField(fieldName)
    getCorrespondingElement(field).getOrElse(field).asInstanceOf[IField]
  }
  
  override def getMethod(selector : String, parameterTypeSignatures : Array[String]) : IMethod = {
    val method = super.getMethod(selector, parameterTypeSignatures)
    getCorrespondingElement(method).getOrElse(method).asInstanceOf[IMethod]
  }
}

class ScalaClassElement(parent : JavaElement, name : String)
  extends ScalaSourceTypeElement(parent, name) {
  override def getImageDescriptor = ScalaImages.SCALA_CLASS
}

class ScalaAnonymousClassElement(parent : JavaElement, name : String)
  extends ScalaClassElement(parent, "") {
    override def getLabelText(flags : Long) = if (name != null ) "new "+name+" {...}" else "new {...}"
}

class ScalaTraitElement(parent : JavaElement, name : String)
  extends ScalaSourceTypeElement(parent, name) {
  override def getImageDescriptor = ScalaImages.SCALA_TRAIT
}

class ScalaModuleElement(parent : JavaElement, name : String, synthetic : Boolean)
  extends ScalaSourceTypeElement(parent, name+"$") {
  override def scalaName = name
  override def getLabelText(flags : Long) = name
  override def getImageDescriptor = ScalaImages.SCALA_OBJECT
  override def isVisible = !synthetic
}

class ScalaDefElement(parent : JavaElement, name: String, paramTypes : Array[String], synthetic : Boolean, display : String)
  extends SourceMethod(parent, name, paramTypes) with ScalaElement with IMethodOverrideInfo {
  override def getLabelText(flags : Long) = display
  override def isVisible = !synthetic && !getElementInfo.isInstanceOf[ScalaSourceConstructorInfo]
}

class ScalaFunctionElement(declaringType : JavaElement, parent : JavaElement, name: String, paramTypes : Array[String], display : String)
  extends SourceMethod(parent, name, paramTypes) with ScalaElement {
  override def getDeclaringType() : IType = declaringType.asInstanceOf[IType]
  override def getLabelText(flags : Long) = display
}

class ScalaAccessorElement(parent : JavaElement, name: String, paramTypes : Array[String])
  extends SourceMethod(parent, name, paramTypes) with ScalaElement {
  override def isVisible = false
}

class ScalaValElement(parent : JavaElement, name: String, display : String)
  extends SourceField(parent, name) with ScalaFieldElement {
  override def getLabelText(flags : Long) = display
  override def getImageDescriptor = {
    val flags = getFlags
    if ((flags & ClassFileConstants.AccPublic) != 0)
      ScalaImages.PUBLIC_VAL
    else if ((flags & ClassFileConstants.AccProtected) != 0)
      ScalaImages.PROTECTED_VAL
    else
      ScalaImages.PRIVATE_VAL
  }  
} 

class ScalaVarElement(parent : JavaElement, name: String, display : String)
  extends SourceField(parent, name) with ScalaFieldElement {
  override def getLabelText(flags : Long) = display
}

class ScalaTypeElement(parent : JavaElement, name : String, display : String)
  extends SourceField(parent, name) with ScalaFieldElement {
  override def getLabelText(flags : Long) = display
  override def getImageDescriptor = ScalaImages.SCALA_TYPE
} 

class ScalaLocalVariableElement(
  parent : JavaElement, name : String,
  declarationSourceStart : Int, declarationSourceEnd : Int, nameStart : Int, nameEnd : Int,
  typeSignature : String,
  display : String) extends LocalVariable(
  parent, name, declarationSourceStart, declarationSourceEnd, nameStart, nameEnd, typeSignature, null) with
  ScalaElement {
  override def getLabelText(flags : Long) = display
}

class ScalaModuleInstanceElement(parent : JavaElement)
  extends SourceField(parent, "MODULE$") with ScalaFieldElement {
  override def getLabelText(flags : Long) = getElementName
  override def isVisible = false
}

object ScalaMemberElementInfo extends ReflectionUtils {
  val jeiClazz = Class.forName("org.eclipse.jdt.internal.core.JavaElementInfo")
  val meiClazz = Class.forName("org.eclipse.jdt.internal.core.MemberElementInfo")
  val aiClazz = Class.forName("org.eclipse.jdt.internal.core.AnnotatableInfo")
  val sreiClazz = Class.forName("org.eclipse.jdt.internal.core.SourceRefElementInfo")
  val setFlagsMethod = getDeclaredMethod(meiClazz, "setFlags", classOf[Int])
  val getNameSourceStartMethod = try {
    getDeclaredMethod(meiClazz, "getNameSourceStart")
  } catch {
    case _ : NoSuchMethodException => getDeclaredMethod(aiClazz, "getNameSourceStart")
  }
  val getNameSourceEndMethod = try {
    getDeclaredMethod(meiClazz, "getNameSourceEnd")
  } catch {
    case _ : NoSuchMethodException => getDeclaredMethod(aiClazz, "getNameSourceEnd")
  }
  val setNameSourceStartMethod = try {
    getDeclaredMethod(meiClazz, "setNameSourceStart", classOf[Int])
  } catch {
    case _ : NoSuchMethodException => getDeclaredMethod(aiClazz, "setNameSourceStart", classOf[Int])
  }
  val setNameSourceEndMethod = try {
    getDeclaredMethod(meiClazz, "setNameSourceEnd", classOf[Int])
  } catch {
    case _ : NoSuchMethodException => getDeclaredMethod(aiClazz, "setNameSourceEnd", classOf[Int])
  }
  val setSourceRangeStartMethod = getDeclaredMethod(sreiClazz, "setSourceRangeStart", classOf[Int])
  val setSourceRangeEndMethod = getDeclaredMethod(sreiClazz, "setSourceRangeEnd", classOf[Int])
  val getDeclarationSourceStartMethod = getDeclaredMethod(sreiClazz, "getDeclarationSourceStart")
  val getDeclarationSourceEndMethod = getDeclaredMethod(sreiClazz, "getDeclarationSourceEnd")
  val hasChildrenField = try {
    getDeclaredField(jeiClazz, "children")
    true
  } catch {
    case _ : NoSuchFieldException => false 
  }
  val addChildMethod = if (hasChildrenField) getDeclaredMethod(jeiClazz, "addChild", classOf[IJavaElement]) else null
}

trait ScalaMemberElementInfo extends JavaElementInfo {
  import ScalaMemberElementInfo._
  import java.lang.Integer
  
  def addChild0(child : IJavaElement) : Unit

  def setFlags0(flags : Int) = setFlagsMethod.invoke(this, new Integer(flags))
  def getNameSourceStart0 : Int = getNameSourceStartMethod.invoke(this).asInstanceOf[Integer].intValue
  def getNameSourceEnd0 : Int = getNameSourceEndMethod.invoke(this).asInstanceOf[Integer].intValue
  def setNameSourceStart0(start : Int) = setNameSourceStartMethod.invoke(this, new Integer(start)) 
  def setNameSourceEnd0(end : Int) = setNameSourceEndMethod.invoke(this, new Integer(end)) 
  def getDeclarationSourceStart0 : Int = getDeclarationSourceStartMethod.invoke(this).asInstanceOf[Integer].intValue
  def getDeclarationSourceEnd0 : Int = getDeclarationSourceEndMethod.invoke(this).asInstanceOf[Integer].intValue
  def setSourceRangeStart0(start : Int) : Unit = setSourceRangeStartMethod.invoke(this, new Integer(start))
  def setSourceRangeEnd0(end : Int) : Unit = setSourceRangeEndMethod.invoke(this, new Integer(end))
}

trait AuxChildrenElementInfo extends JavaElementInfo {
  import ScalaMemberElementInfo._

  var auxChildren : Array[IJavaElement] = if (hasChildrenField) null else new Array(0)

  override def getChildren = if (hasChildrenField) super.getChildren else auxChildren
  
  def addChild0(child : IJavaElement) : Unit =
    if (hasChildrenField)
      addChildMethod.invoke(this, child)
    else if (auxChildren.length == 0)
      auxChildren = Array(child)
    else if (!auxChildren.contains(child))
      auxChildren = auxChildren ++ Seq(child)
}

class ScalaElementInfo extends SourceTypeElementInfo with ScalaMemberElementInfo {
  import ScalaMemberElementInfo._
  
  override def addChild0(child : IJavaElement) : Unit = {
    if (hasChildrenField)
      addChildMethod.invoke(this, child)
    else if (children.length == 0)
      children = Array(child)
    else if (!children.contains(child))
      children = children ++ Seq(child)
  }
  
  override def setHandle(handle : IType) = super.setHandle(handle)
  override def setSuperclassName(superclassName : Array[Char]) = super.setSuperclassName(superclassName)
  override def setSuperInterfaceNames(superInterfaceNames : Array[Array[Char]]) = super.setSuperInterfaceNames(superInterfaceNames)
}

trait FnInfo extends SourceMethodElementInfo with ScalaMemberElementInfo {
  override def setArgumentNames(argumentNames : Array[Array[Char]]) = super.setArgumentNames(argumentNames)
  def setReturnType(returnType : Array[Char])
  override def setExceptionTypeNames(exceptionTypeNames : Array[Array[Char]]) = super.setExceptionTypeNames(exceptionTypeNames)
}

class ScalaSourceConstructorInfo extends SourceConstructorInfo with FnInfo with AuxChildrenElementInfo {
  override def setReturnType(returnType : Array[Char]) = super.setReturnType(returnType)
}

class ScalaSourceMethodInfo extends SourceMethodInfo with FnInfo with AuxChildrenElementInfo {
  override def setReturnType(returnType : Array[Char]) = super.setReturnType(returnType)
}

class ScalaSourceFieldElementInfo extends SourceFieldElementInfo with ScalaMemberElementInfo with AuxChildrenElementInfo {
  override def setTypeName(name : Array[Char]) = super.setTypeName(name)
}
