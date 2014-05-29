/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy

/**
 * Names of Java boxed types, used in multiple places.
 */
object JavaBoxed {

  val String = classOf[java.lang.String].getName

  val Boolean = classOf[java.lang.Boolean].getName

  val Integer = classOf[java.lang.Integer].getName

  val Double = classOf[java.lang.Double].getName

  val Float = classOf[java.lang.Float].getName

  val Character = classOf[java.lang.Character].getName

  val Long = classOf[java.lang.Long].getName

  val Byte = classOf[java.lang.Byte].getName

  val Short = classOf[java.lang.Short].getName

  val all = Set(Integer, Double, Float, Long, Character, Boolean, Byte, Short)
}

/**
 * Names of Java unboxed types, used in multiple places.
 */
object JavaPrimitives {

  val boolean = "boolean"

  val int = "int"

  val double = "double"

  val float = "float"

  val char = "char"

  val long = "long"

  val byte = "byte"

  val short = "short"

  /** Regex for matching Java arrays */
  val Array = """(.*)\[\]""".r
}

/**
 * Names of Scala unified types, used in multiple places.
 */
object ScalaPrimitivesUnified {

  val Int = "scala.Int"

  val Double = "scala.Double"

  val Float = "scala.Float"

  val Long = "scala.Long"

  val Char = "scala.Char"

  val Boolean = "scala.Boolean"

  val Byte = "scala.Byte"

  val Short = "scala.Short"

  val all = Set(Int, Double, Float, Long, Char, Boolean, Byte, Short)
}

/**
 * Scala rich types wrappers.
 */
object ScalaRichTypes {
  val Boolean = classOf[scala.runtime.RichBoolean].getName
  val Byte = classOf[scala.runtime.RichByte].getName
  val Char = classOf[scala.runtime.RichChar].getName
  val Double = classOf[scala.runtime.RichDouble].getName
  val Float = classOf[scala.runtime.RichFloat].getName
  val Int = classOf[scala.runtime.RichInt].getName
  val Long = classOf[scala.runtime.RichLong].getName
  val Short = classOf[scala.runtime.RichShort].getName

  val all = Set(Int, Double, Float, Long, Char, Boolean, Byte, Short)
}

object ScalaFunctions {

  val PartialFunction = "scala.PartialFunction"

  val Function0 = "scala.Function0"
  val Function1 = "scala.Function1"
  val Function2 = "scala.Function2"
  val Function3 = "scala.Function3"
  val Function4 = "scala.Function4"
  val Function5 = "scala.Function5"
  val Function6 = "scala.Function6"
  val Function7 = "scala.Function7"
  val Function8 = "scala.Function8"
  val Function9 = "scala.Function9"
  val Function10 = "scala.Function10"
  val Function11 = "scala.Function11"
  val Function12 = "scala.Function12"
  val Function13 = "scala.Function13"
  val Function14 = "scala.Function14"
  val Function15 = "scala.Function15"
  val Function16 = "scala.Function16"
  val Function17 = "scala.Function17"
  val Function18 = "scala.Function18"
  val Function19 = "scala.Function19"
  val Function20 = "scala.Function20"
  val Function21 = "scala.Function21"
  val Function22 = "scala.Function22"
}

object ScalaOther {

  val constructorFunctionName = "<init>"

  val nothingType = "scala.Nothing"

  val unitType = "scala.Unit"

  val unitLiteral = "()"

  val arrayType = "scala.Array"

  val partialFunctionType = classOf[PartialFunction[_, _]].getName

  /** Supported methods from `scala.Dynamic` */
  val dynamicTraitMethods = Set(
    "updateDynamic",
    "selectDynamic",
    "applyDynamic")

  val scalaList = "scala.collection.immutable.::"

  val rangeInclusive = "scala.collection.immutable.Range$Inclusive"
  val range = "scala.collection.immutable.Range"

  /** Regex for matching Scala arrays */
  val Array = """Array\[(.*)\]""".r

  def Array(typeName: String) = s"Array[$typeName]"

  val nil = "scala.collection.immutable.Nil"
  // strange value that shows up instead of above one
  val thisNil = "immutable.this.Nil"

  val list = "scala.collection.immutable.List"
  // strange value that shows up instead of above one
  val thisList = "immutable.this.List"
}

object DebuggerSpecific {

  /** Prefix for object in generated stubs. */
  val objNamePrefix = "$o_"

  /*  JdiProxy - in all variants */
  val proxyName = classOf[JdiProxy].getSimpleName
  val proxyFullName = classOf[JdiProxy].getName
  val proxyObjectFullName = JdiContext.toObject(proxyFullName)

  /* JdiContext in all variants */
  val contextName = classOf[JdiContext].getSimpleName
  val contextFullName = classOf[JdiContext].getName
  val contextObjFullName = JdiContext.toObject(contextFullName)

  /** Name of placeholder method, used in reflective compilation. */
  val placeholderName = "placeholder"

  /** Name of placeholder function method, used in reflective compilation. */
  val placeholderPartialFunctionName = "placeholderPartialFunction"

  /** Name of placeholder partial function method, used in reflective compilation. */
  val placeholderFunctionName = "placeholderFunction"

  /** Name of proxy method, used in reflective compilation. */
  val proxyMethodName = "proxy"

  /** Name of proxy method, used in reflective compilation. */
  val valueProxyMethodName = "valueProxy"

  /** Name of proxy method, used in reflective compilation. */
  val objectProxyMethodName = "objectProxy"

  /** Name of stringify method, used in reflective compilation. */
  val stringifyMethodName = "stringify"

  /** Name of context val on tolevel function for expression. */
  val contextParamName = "__context"

  /** Name of `this` stub. */
  val thisValName = "__this"

  /** Name of this proxy method, used in reflective compilation. */
  val thisObjectProxyMethodName = "thisObjectProxy"

  /** Name of invoke method method. */
  val invokeMethodName = "invokeMethod"
}