/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context

import scala.collection.JavaConversions._
import scala.reflect.NameTransformer

import org.scalaide.debug.internal.expression.proxies.ArrayJdiProxy
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.NullJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.UnitJdiProxy

import com.sun.jdi.ArrayReference
import com.sun.jdi.ArrayType
import com.sun.jdi.ClassType
import com.sun.jdi.ObjectReference
import com.sun.jdi.StringReference
import com.sun.jdi.Type
import com.sun.jdi.Value

import Names.Debugger
import Names.Java
import Names.Scala

/**
 * Part of JdiContext responsible for converting proxies to their string representations.
 */
trait Stringifier {
  self: JdiContext =>

  /** Calls `toString` on given proxy, returns jdi String reference. */
  final def callToString(proxy: JdiProxy): StringReference =
    invokeUnboxed[StringReference](proxy, None, "toString", Seq.empty)

  /** Calls `scala.runtime.ScalaRunTime.stringOf` on given proxy, returns jdi String reference. */
  final def callScalaRuntimeStringOf(proxy: JdiProxy): StringReference = {
    val scalaRuntime = objectByName("scala.runtime.ScalaRunTime")
    val stringOf = methodOn(scalaRuntime, "stringOf", arity = 1)
    scalaRuntime.invokeMethod(currentThread(), stringOf, List(proxy.__underlying)).asInstanceOf[StringReference]
  }

  /**
   * Calls `toString` on given proxy, returns StringJdiProxy.
   *
   * WARNING - this method is used in reflective compilation.
   * If you change its name, package or behavior, make sure to change it also.
   */
  final def stringify(proxy: JdiProxy): JdiProxy =
    this.invokeMethod(proxy, None, "toString")

  /**
   * String representation of given proxy. Contains value and type.
   */
  final def show(proxy: JdiProxy, withType: Boolean = true): String = proxy match {
    case _: NullJdiProxy => formatString(Scala.nullLiteral, Scala.nullType)

    case _: UnitJdiProxy => formatString(Scala.unitLiteral, Scala.unitType)

    case nulledProxy if nulledProxy.__underlying == null => formatString(Scala.nullLiteral, Scala.nullType)

    case array: ArrayJdiProxy[_] => handleArray(array)

    case other => handle(other, withType)
  }

  private def formatString(value: String, typeName: String) = s"$value (of type: $typeName)"

  /**
   * Prints Arrays - it is pretty complicated, as arrays are reifiable on JVM and we have to handle nested array types.
   */
  private def handleArray(array: ArrayJdiProxy[_]): String =
    formatString(callScalaRuntimeStringOf(array).value, handleArrayTpe(array))

  private def handleArrayTpe(array: ArrayJdiProxy[_]): String = {
    // handle nested array types
    def innerTpe(tpe: Type): String = tpe match {
      case arrayType: ArrayType =>
        val argumentType = innerTpe(arrayType.componentType)
        Scala.Array(TypeNames.javaNameToScalaName(argumentType))
      case other =>
        TypeNames.javaNameToScalaName(other.name)
    }

    innerTpe(array.__underlying.`type`)
  }

  def isLambda(name: String): Boolean = {
    //example class name: __wrapper$1$c0fa68b3bc094e8387b36b16f8fde8b5.__wrapper$1$c0fa68b3bc094e8387b36b16f8fde8b5$CustomFunction$1
    val nameArray = name.split('$')
    def lenghtOk = nameArray.length > 3
    def startsWithWrapper = nameArray(0) == "__wrapper"
    def containsNewClassName = nameArray(nameArray.length - 2) == Debugger.newClassName
    lenghtOk && startsWithWrapper && containsNewClassName
  }

  private def typeOfProxy(proxy: JdiProxy): String = {
    val underlyingType = proxy.__underlying.referenceType.name
    val typeDecoded = NameTransformer.decode(underlyingType)
    if (isLambda(typeDecoded)) Debugger.lambdaType else typeDecoded
  }

  private def handle(proxy: JdiProxy, withType: Boolean): String = {
    val stringValue = callScalaRuntimeStringOf(proxy).value
    if (withType) formatString(stringValue, typeOfProxy(proxy)) else stringValue
  }

}
