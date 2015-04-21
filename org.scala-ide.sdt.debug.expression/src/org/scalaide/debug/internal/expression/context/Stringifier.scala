/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context

import scala.collection.JavaConversions._
import scala.reflect.NameTransformer

import Names._

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

  /**
   * Prints Arrays - it is pretty complicated, as arrays are reifiable on JVM and we have to handle nested array types.
   */
  private def handleArray(array: ArrayJdiProxy[_]): String =
    formatString(handleArrayValue(array), handleArrayTpe(array))

  private def handleArrayValue(array: ArrayJdiProxy[_]): String = {
    // workaround for a crappy Eclipse's implementation of getValues which throws exceptions in the case of
    // empty arrays instead of returning empty list
    def handleEmptyArray(arrayRef: ArrayReference): List[Value] =
      if (arrayRef.length() != 0) arrayRef.getValues().toList else Nil

    // responsible for converting value to its string rep
    def inner(value: Value): String = {
      val result: String = value match {
        case null =>
          "null"
        // to avoid 'java.lang.Double (id=423)' in results
        case objectRef: ObjectReference if Java.boxed.all.contains(objectRef.`type`.toString) =>
          val field = objectRef.referenceType.asInstanceOf[ClassType].fieldByName("value")
          objectRef.getValue(field).toString
        // handle nested arrays
        case array: ArrayReference =>
          handleEmptyArray(array)
            .map(inner)
            .mkString("Array(", ", ", ")")
        case other =>
          other.toString
      }
      handleStringQuotes(result)
    }

    handleEmptyArray(array.__underlying)
      .map(inner)
      .mkString("Array(", ", ", ")")
  }

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

  // mimics Scala REPL behaviour for displaying strings:
  // only print quotes if string is empty or have trailing whitespace
  private def handleStringQuotes(result: String): String = {
    var x = result
    if (x.isEmpty) '"' + x + '"'
    else {
      val isString = x.head == '"' && x.last == '"'
      // remove " from strings
      if (isString) x = x.drop(1).dropRight(1)
      def haveTrailingWhitespace = x.head.isWhitespace || x.last.isWhitespace
      // if element is empty or starts/ends with whitespace add quotes back
      if (x.isEmpty || haveTrailingWhitespace) '"' + x + '"'
      else x
    }
  }

  private def handle(proxy: JdiProxy, withType: Boolean): String = {
    val stringValue = handleStringQuotes(callToString(proxy).value)
    if (withType) formatString(stringValue, typeOfProxy(proxy)) else stringValue
  }

}
