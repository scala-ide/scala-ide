/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.context

import scala.collection.JavaConversions._
import scala.reflect.NameTransformer

import org.scalaide.debug.internal.expression.Names
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.TypeNameMappings
import org.scalaide.debug.internal.expression.proxies.ArrayJdiProxy
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.NullJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.UnitJdiProxy

import com.sun.jdi.ArrayReference
import com.sun.jdi.ArrayType
import com.sun.jdi.ClassType
import com.sun.jdi.ObjectReference
import com.sun.jdi.StringReference
import com.sun.jdi.Value

/**
 * Part of JdiContext responsible for converting proxies to their string representations.
 */
trait Stringifier {
  self: JdiMethodInvoker =>

  /** Calls `toString` on given proxy, returns jdi String reference. */
  final def callToString(proxy: JdiProxy): StringReference =
    invokeUnboxed[StringReference](proxy, None, "toString", Seq.empty)

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

  private def handleArray(array: ArrayJdiProxy[_]): String = {

    // to avoid 'java.lang.Double (id=423)' in results
    def handleBoxedPrimitive(value: Value): String = value match {
      case objectRef: ObjectReference if Java.boxed.all.contains(objectRef.`type`.toString) =>
        val field = objectRef.referenceType.asInstanceOf[ClassType].fieldByName("value")
        objectRef.getValue(field).toString
      case other => other.toString
    }

    // responsible for converting value to its string rep
    def inner(value: Value): String = {
      var x = Option(value).fold("null")(handleBoxedPrimitive)
      val isString = x.head == '\"' && x.last == '\"'
      // remove " from strings
      if (isString) x = x.drop(1).dropRight(1)
      val haveTrailingWhitespace = x.head.isWhitespace || x.last.isWhitespace
      // if elements starts/ends with whitespace add them back
      if (haveTrailingWhitespace) "\"" + x + "\"" else x
    }

    // workaround for a crappy Eclipse's implementation of getValues which throws exceptions in the case of
    // empty arrays instead of returning empty list
    def handleEmptyArray(arrayRef: ArrayReference): List[Value] =
      if (arrayRef.length() != 0) arrayRef.getValues().toList else Nil

    val stringValue = handleEmptyArray(array.__underlying)
      .map(inner)
      .mkString("Array(", ", ", ")")

    val typeString = array.__underlying.`type` match {
      case arrayType: ArrayType =>
        val argumentType = TypeNameMappings.javaNameToScalaName(arrayType.componentTypeName)
        Scala.Array(argumentType)
    }
    formatString(stringValue, typeString)
  }

  def isLambda(name: String): Boolean = {
    //example class name: __wrapper$1$c0fa68b3bc094e8387b36b16f8fde8b5.__wrapper$1$c0fa68b3bc094e8387b36b16f8fde8b5$CustomFunction$1
    val nameArray = name.split('$')
    def lenghtOk = nameArray.length > 3
    def startsWithWrapper = nameArray(0) == "__wrapper"
    def containsNewClassName = nameArray(nameArray.length - 2) == Names.Debugger.newClassName
    lenghtOk && startsWithWrapper && containsNewClassName
  }

  private def typeOfProxy(proxy: JdiProxy): String = {
    val underlyingType = proxy.__underlying.referenceType.name
    val typeDecoded = NameTransformer.decode(underlyingType)
    if (isLambda(typeDecoded)) Names.Debugger.lambdaType else typeDecoded
  }

  private def handle(proxy: JdiProxy, withType: Boolean): String = {
    val stringValue = callToString(proxy).value
    if (withType) formatString(stringValue, typeOfProxy(proxy)) else stringValue
  }

}