/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.context

import scala.reflect.runtime.universe.TermName
import scala.util.Try

import org.scalaide.debug.internal.expression.Names.Debugger
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.TypeNameMappings

import com.sun.jdi.{ReferenceType, ObjectReference, Type, Value}

/**
 * Implementation of VariableContext based on ThreadReference.
 */
private[context] trait JdiVariableContext
  extends VariableContext {
  self: JdiContext =>

  protected def expressionClassLoader: ClassLoader

  override def syntheticVariables: Seq[TermName] = transformationContext.thisFields

  override def syntheticImports: Seq[String] = transformationContext.imports

  override def implementValue(name: TermName): Option[String] = transformationContext.implementValue(name)

  /** See [[org.scalaide.debug.internal.expression.context.VariableContext]] */
  override def thisPackage: Option[String] = thisObject.flatMap { obj =>
    val signature = obj.`type`.signature
    signature.head match {
      case 'L' => Some(signature.tail.split("/").init.mkString(".")).filterNot(_.isEmpty)
      case _ => None
    }
  }

  /** Type of given value, as String and information about generic type) */
  def nameAndGenericName(typeName: Type): (String, Option[String]) = {
    val isGeneric = typeName match {
      case refType: ReferenceType => Option(refType.genericSignature())
        .filter(_.startsWith("<")) // If there are any generic parameters generic looks like <A:...
      case _ => None
    }
    if (typeName == null) Scala.nullType -> isGeneric
    else TypeNameMappings.javaNameToScalaName(typeName.toString) -> isGeneric
  }

  /** See [[org.scalaide.debug.internal.expression.context.VariableContext]] */
  override def typeOf(variableName: TermName): Option[(String, Option[String])] = {
    import Debugger._

    // null-safe Value.type
    def typeOfValue(value: Value): Type = if (value == null) null else value.`type`()

    val value = transformationContext.typeFor(variableName)
      .orElse(valueFromFrame(currentFrame(), variableName.toString).map(typeOfValue))

    value.map(nameAndGenericName).map {
      case (name, genName) =>
        val realName = if (onClassPath(expressionClassLoader, name)) getScalaNameFromType(name) else proxyName
        (realName, genName)
    }
  }

  /** Changes all `$` and `_` to `.`, if type ends with `$` changes it to `.type` */
  private def escape(name: String): String = {
    val replaced = name.replaceAll( """(\$|_)""", """\.""")
    if (replaced.endsWith(".")) replaced + "type" else replaced
  }

  /**
   * convert jvm type name into scala-code-like name
   * replace all $ with . and if is an object add type and end
   */
  private def getScalaNameFromType(name: String): String = {
    if (name.endsWith("$")) escape(name)
    else name
  }


  /** Reference to object referenced by `this` */
  private def thisObject: Option[ObjectReference] = Option(currentFrame.thisObject)

  /**
   * Checks if type exists on classpath.
   * Looks up `typeName`, `java.typeName` and `scala.typeName`.
   */
  private def onClassPath(classLoader: ClassLoader, typeName: String): Boolean = {
    def tryClassName(typeName: String) = Try(classLoader.loadClass(typeName)).isSuccess

    val prefixes = Seq("", "scala.", "java.")

    typeName match {
      case Scala.Array(innerType) => onClassPath(classLoader, innerType)
      case other => prefixes.exists(prefix => tryClassName(prefix + typeName))
    }
  }
}