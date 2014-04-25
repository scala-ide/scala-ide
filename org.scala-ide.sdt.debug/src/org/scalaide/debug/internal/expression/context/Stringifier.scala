/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.context

import scala.collection.JavaConversions._
import scala.reflect.NameTransformer

import org.scalaide.debug.internal.expression.Names.Debugger
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.TypeNameMappings
import org.scalaide.debug.internal.expression.proxies.AbstractFunctionJdiProxy
import org.scalaide.debug.internal.expression.proxies.ArrayJdiProxy
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.expression.proxies.StringJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.NullJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.UnitJdiProxy

import com.sun.jdi.ArrayReference
import com.sun.jdi.ArrayType
import com.sun.jdi.StringReference
import com.sun.jdi.Value

/**
 * Part of JdiContext responsible for converting proxies to their string representations.
 */
trait Stringifier {
  self: MethodInvoker =>

  /** Calls `toString` on given proxy, returns jdi String reference. */
  final def callToString(proxy: JdiProxy): StringReference =
    invokeUnboxed[StringReference](proxy, None, "toString", Seq.empty)

  /**
   * Calls `toString` on given proxy, returns StringJdiProxy.
   *
   * WARNING - this method is used in reflective compilation.
   * If you change it's name, package or behavior, make sure to change it also.
   */
  final def stringify(proxy: JdiProxy): StringJdiProxy =
    this.invokeMethod[StringJdiProxy](proxy, None, "toString")

  /**
   * String representation of given proxy. Contains value and type.
   */
  def show(proxy: JdiProxy, withType: Boolean = true): String = proxy match {
    case _: NullJdiProxy => formatString(Scala.nullLiteral, Scala.nullType)

    case _: UnitJdiProxy => formatString(Scala.unitLiteral, Scala.unitType)

    case nulledProxy if nulledProxy.__underlying == null => formatString(Scala.nullLiteral, Scala.nullType)

    case fun: AbstractFunctionJdiProxy => formatString(callToString(fun).value, Debugger.lambdaType)

    case array: ArrayJdiProxy[_] => handleArray(array)

    case other => handle(other, withType)
  }

  private def formatString(value: String, typeName: String) = s"$value (of type: $typeName)"

  private def handleArray(array: ArrayJdiProxy[_]): String = {
    // responsible for converting value to its string rep
    def inner(value: Value): String = {
      var x = Option(value).fold("null")(_.toString)
      val isString = x.head == '\"' && x.last == '\"'
      // remove " from strings
      if (isString) x = x.drop(1).dropRight(1)
      val haveTrailingWhitespace = x.head.isWhitespace || x.last.isWhitespace
      // if elements starts/ends with whitespace add them back
      if (haveTrailingWhitespace) "\"" + x + "\"" else x
    }

    // workaround for crappy Eclipse implementation of getValues which throw exceptions on
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

  private def handle(proxy: JdiProxy, withType: Boolean): String = {
    val stringValue = callToString(proxy).value
    val underlyingType = proxy.__underlying.referenceType.name
    val typeDecoded = NameTransformer.decode(underlyingType)
    if (withType) formatString(stringValue, typeDecoded) else stringValue
  }

}