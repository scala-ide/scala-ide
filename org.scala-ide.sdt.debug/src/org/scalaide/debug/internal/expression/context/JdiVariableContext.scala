/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.context

import scala.util.Try

import org.scalaide.debug.internal.expression.DebuggerSpecific
import org.scalaide.debug.internal.expression.JavaPrimitives
import org.scalaide.debug.internal.expression.ScalaOther
import org.scalaide.debug.internal.expression.ScalaPrimitivesUnified

import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

/**
 * Implementation of VariableContext based on ThreadReference.
 */
private[context] trait JdiVariableContext
  extends VariableContext {
  self: JdiContext =>

  protected def expressionClassLoader: ClassLoader

  /** See [[org.scalaide.debug.internal.expression.VariableContext]] */
  override def getThisPackage: Option[String] = {
    val signature = topFrame.thisObject.`type`.signature
    signature.head match {
      case 'L' => Some(signature.tail.split("/").init.mkString(".")).filterNot(_.isEmpty)
      case _ => None
    }
  }

  /** See [[org.scalaide.debug.internal.expression.VariableContext]] */
  override def getType(variableName: String): Option[String] = {
    import DebuggerSpecific._

    val name = if (variableName == thisValName) Some(topFrame.thisObject) else valueFromFrame(topFrame, variableName)

    name.map(valueTypeAsString)
      .map(name => if (onClassPath(expressionClassLoader, name)) name else proxyName)
      .map(getScalaNameFromType)
  }

  /** Changes all `$` and `_` to `.`, if type ends with `$` changes it to `.type` */
  private def escape(name: String): String = {
    val replaced = name.replaceAll("""(\$|_)""", """\.""")
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

  /** Type of given value, as String */
  private def valueTypeAsString(value: Value): String = javaPrimitivesToScala(value.`type`.toString)

  /** Maps java primitives to Scala unified names. Other names are unchanged. */
  private def javaPrimitivesToScala(typeName: String): String = typeName match {
    case JavaPrimitives.byte => ScalaPrimitivesUnified.Byte
    case JavaPrimitives.short => ScalaPrimitivesUnified.Short
    case JavaPrimitives.int => ScalaPrimitivesUnified.Int
    case JavaPrimitives.double => ScalaPrimitivesUnified.Double
    case JavaPrimitives.float => ScalaPrimitivesUnified.Float
    case JavaPrimitives.long => ScalaPrimitivesUnified.Long
    case JavaPrimitives.char => ScalaPrimitivesUnified.Char
    case JavaPrimitives.boolean => ScalaPrimitivesUnified.Boolean
    case JavaPrimitives.Array(innerType) => ScalaOther.Array(javaPrimitivesToScala(innerType))
    case other => other
  }

  /** Reference to object referenced by `this` */
  private def thisObject: ObjectReference = topFrame.thisObject

  /**
   * Checks if type exists on classpath.
   * Looks up `typeName`, `java.typeName` and `scala.typeName`.
   */
  private def onClassPath(classLoader: ClassLoader, typeName: String): Boolean = {
    def tryClassName(typeName: String) = Try(classLoader.loadClass(typeName)).isSuccess

    val prefixes = Seq("", "scala.", "java.")

    typeName match {
      case ScalaOther.Array(innerType) => prefixes.exists(prefix => tryClassName(prefix + innerType))
      case other => prefixes.exists(prefix => tryClassName(prefix + typeName))
    }
  }
}