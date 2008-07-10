/*
 * Copyright 2005-2008 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import java.lang.Integer

import org.eclipse.jdt.core.{ IJavaElement, IType }
import org.eclipse.jdt.internal.core.{
  JavaElement, SourceConstructorInfo, SourceField, SourceFieldElementInfo, SourceMethod,
  SourceMethodElementInfo, SourceMethodInfo, SourceType, SourceTypeElementInfo } 

trait ScalaElement {
  def getElementInfo : AnyRef
  def getElementName : String
  def scalaName : String = getElementName
}

trait ScalaFieldElement extends ScalaElement

class ScalaClassElement(parent : JavaElement, name : String) extends SourceType(parent, name) with ScalaElement {}

class ScalaTraitElement(parent : JavaElement, name : String) extends SourceType(parent, name) with ScalaElement {}

class ScalaModuleElement(parent : JavaElement, name : String) extends SourceType(parent, name+"$") with ScalaElement {
  override def scalaName = name
}
  
class ScalaDefElement(parent : JavaElement, name: String, paramTypes : Array[String])
  extends SourceMethod(parent, name, paramTypes) with ScalaElement {} 

class ScalaFunctionElement(declaringType : JavaElement, parent : JavaElement, name: String, paramTypes : Array[String])
  extends SourceMethod(parent, name, paramTypes) with ScalaElement {
  override def getDeclaringType() : IType = declaringType.asInstanceOf[IType]
}

class ScalaAccessorElement(parent : JavaElement, name: String, paramTypes : Array[String])
  extends SourceMethod(parent, name, paramTypes) with ScalaElement {} 

class ScalaValElement(parent : JavaElement, name: String)
  extends SourceField(parent, name) with ScalaFieldElement {} 

class ScalaVarElement(parent : JavaElement, name: String)
  extends SourceField(parent, name) with ScalaFieldElement {} 

class ScalaTypeElement(parent : JavaElement, name : String)
  extends SourceField(parent, name) with ScalaFieldElement {} 

class ScalaModuleInstanceElement(parent : JavaElement)
  extends SourceField(parent, "MODULE$") with ScalaFieldElement {}
  
object ScalaMemberElementInfo extends ReflectionUtils {
  val meiClazz = Class.forName("org.eclipse.jdt.internal.core.MemberElementInfo")
  val sreiClazz = Class.forName("org.eclipse.jdt.internal.core.SourceRefElementInfo")
  val setFlagsMethod = getMethod(meiClazz, "setFlags", classOf[Int])
  val setNameSourceStartMethod = getMethod(meiClazz, "setNameSourceStart", classOf[Int])
  val setNameSourceEndMethod = getMethod(meiClazz, "setNameSourceEnd", classOf[Int])
  val setSourceRangeStartMethod = getMethod(sreiClazz, "setSourceRangeStart", classOf[Int])
  val setSourceRangeEndMethod = getMethod(sreiClazz, "setSourceRangeEnd", classOf[Int])
}

trait ScalaMemberElementInfo {
  import ScalaMemberElementInfo._
  def setFlags0(flags : Int) = setFlagsMethod.invoke(this, new Integer(flags))
  def setNameSourceStart0(start : Int) = setNameSourceStartMethod.invoke(this, new Integer(start)) 
  def setNameSourceEnd0(end : Int) = setNameSourceEndMethod.invoke(this, new Integer(end)) 
  def setSourceRangeStart0(start : Int) : Unit = setSourceRangeStartMethod.invoke(this, new Integer(start))
  def setSourceRangeEnd0(end : Int) : Unit = setSourceRangeEndMethod.invoke(this, new Integer(end))
}

class ScalaElementInfo extends SourceTypeElementInfo with ScalaMemberElementInfo {
  override def setHandle(handle : IType) = super.setHandle(handle)
  override def setSuperclassName(superclassName : Array[Char]) = super.setSuperclassName(superclassName)
  override def setSuperInterfaceNames(superInterfaceNames : Array[Array[Char]]) = super.setSuperInterfaceNames(superInterfaceNames)
}

trait DefInfo extends SourceMethodElementInfo with ScalaMemberElementInfo {
  override def setArgumentNames(argumentNames : Array[Array[Char]]) = super.setArgumentNames(argumentNames)
  def setReturnType(returnType : Array[Char])
  override def setExceptionTypeNames(exceptionTypeNames : Array[Array[Char]]) = super.setExceptionTypeNames(exceptionTypeNames)
}

class ScalaSourceConstructorInfo extends SourceConstructorInfo with DefInfo {
  override def setReturnType(returnType : Array[Char]) = super.setReturnType(returnType)
}

class ScalaSourceMethodInfo extends SourceMethodInfo with DefInfo {
  override def setReturnType(returnType : Array[Char]) = super.setReturnType(returnType)
}

class ScalaSourceFieldElementInfo extends SourceFieldElementInfo with ScalaMemberElementInfo {
  override def setTypeName(name : Array[Char]) = super.setTypeName(name)
}
