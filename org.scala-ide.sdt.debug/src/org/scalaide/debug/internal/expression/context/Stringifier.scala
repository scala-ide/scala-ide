/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.context

import scala.collection.JavaConversions._
import scala.reflect.NameTransformer

import org.scalaide.debug.internal.expression.TypeNameMappings
import org.scalaide.debug.internal.expression.proxies.ArrayJdiProxy
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.expression.proxies.StringJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.UnitJdiProxy

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
    case unit: UnitJdiProxy => "() (of type: scala.Unit)"

    case array: ArrayJdiProxy => {

      // responsible for converting value to its string rep
      def inner(value: Value): String = {
        var x = value.toString
        val isString = x.head == '\"' && x.last == '\"'
        // remove " from strings
        if (isString) x = x.drop(1).dropRight(1)
        val haveTrailingWhitespace = x.head.isWhitespace || x.last.isWhitespace
        // if elements starts/ends with whitespace add them back
        if (haveTrailingWhitespace) "\"" + x + "\"" else x
      }

      val stringValue = array.underlying.getValues
        .map(inner)
        .mkString("Array(", ", ", ")")

      val typeString = array.underlying.`type` match {
        case arrayType: ArrayType =>
          val argumentType = TypeNameMappings.javaNameToScalaName(arrayType.componentTypeName)
          s"scala.Array[$argumentType]"
      }
      s"$stringValue (of type: $typeString)"
    }

    case _ =>
      val stringValue = callToString(proxy).value
      val underlyingType = proxy.underlying.referenceType.name
      val typeDecoded = NameTransformer.decode(underlyingType)
      if (withType) s"$stringValue (of type: $typeDecoded)" else stringValue
  }
}