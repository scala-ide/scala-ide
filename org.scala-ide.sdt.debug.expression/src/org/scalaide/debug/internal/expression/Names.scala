/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import scala.reflect.runtime.universe
import scala.runtime.RichBoolean
import scala.runtime.RichByte
import scala.runtime.RichChar
import scala.runtime.RichDouble
import scala.runtime.RichFloat
import scala.runtime.RichInt
import scala.runtime.RichLong
import scala.runtime.RichShort

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.BooleanJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.ByteJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.CharJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.DoubleJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.FloatJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.IntJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.LongJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.ShortJdiProxy

/**
 * Names of types and methods used in expression evaluator.
 */
object Names {

  /**
   * Java names.
   */
  object Java {

    /**
     * Names of Java unboxed types, used in multiple places.
     */
    object primitives {

      val boolean = "boolean"

      val int = "int"

      val double = "double"

      val float = "float"

      val char = "char"

      val long = "long"

      val byte = "byte"

      val short = "short"

      val void = "void"

      /** Regex for matching Java arrays */
      val Array = """(.+)\[\]""".r

      def Array(typeName: String) = typeName + "[]"
    }

    /**
     * Names of Java boxed types, used in multiple places.
     */
    object boxed {

      val Boolean = classOf[java.lang.Boolean].getName

      val Integer = classOf[java.lang.Integer].getName

      val Double = classOf[java.lang.Double].getName

      val Float = classOf[java.lang.Float].getName

      val Character = classOf[java.lang.Character].getName

      val Long = classOf[java.lang.Long].getName

      val Byte = classOf[java.lang.Byte].getName

      val Short = classOf[java.lang.Short].getName

      val Void = classOf[java.lang.Void].getName

      val all = Set(Integer, Double, Float, Long, Character, Boolean, Byte, Short, Void)
    }

    val Object = classOf[java.lang.Object].getName

    val String = classOf[java.lang.String].getName
  }

  /**
   * Scala names.
   */
  object Scala {

    val scalaPackageTermName = universe.TermName("scala")

    val constructorMethodName = "<init>"

    val equalsMethodName = "=="

    val notEqualsMethodName = "!="

    val emptyType = "<none>"

    val wildcardType = "?"

    val nothingType = "scala.Nothing"

    val simpleNothingType = "Nothing"

    val boxedUnitType = classOf[scala.runtime.BoxedUnit].getName

    val unitType = "scala.Unit"

    val unitLiteral = "()"

    val nullType = "scala.Null"

    val nullLiteral = "null"

    val arrayType = "scala.Array"

    val partialFunctionType = classOf[PartialFunction[_, _]].getName

    /** Supported methods from `scala.Dynamic` */
    val dynamicTraitMethods = Set(
      "updateDynamic",
      "selectDynamic",
      "applyDynamic")

    val :: = "scala.collection.immutable.::"

    val seq = "scala.collection.Seq"

    val rangeInclusive = "scala.collection.immutable.Range$Inclusive"
    val range = "scala.collection.immutable.Range"

    /**
     * Regex for matching Scala arrays.
     * Matches both `Array[A]` and `scala.Array[A]` and extracts A to group.
     */
    val Array = """(?:scala\.)?Array\[(.+)\]""".r

    def Array(typeName: String) = s"$ArrayRoot[$typeName]"

    val ArrayRoot = "scala.Array"

    val nil = "scala.collection.immutable.Nil"
    // strange value that shows up instead of above one
    val thisNil = "immutable.this.Nil"

    val list = "scala.collection.immutable.List"
    // strange value that shows up instead of above one
    val thisList = "immutable.this.List"

    /**
     * Names of Scala unified types, used in multiple places.
     */
    object primitives {

      val Int = "scala.Int"

      val Double = "scala.Double"

      val Float = "scala.Float"

      val Long = "scala.Long"

      val Char = "scala.Char"

      val Boolean = "scala.Boolean"

      val Byte = "scala.Byte"

      val Short = "scala.Short"

      val Unit = "scala.Unit"

      val all = Set(Int, Double, Float, Long, Char, Boolean, Byte, Short, Unit)

      val allShorten = all.map(_.drop("scala.".size))
    }

    /**
     * Scala rich types wrappers.
     */
    object rich {

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

  }

  /**
   * Names specific to debugger itself.
   */
  object Debugger {

    /** Type used to show for custom user-defined lambdas */
    val lambdaType = "<custom_lambda>"

    val primitiveValueOfProxyMethodName = "__primitiveValue"

    /** JdiProxy - in all variants */
    val proxyName = classOf[JdiProxy].getSimpleName
    val proxyFullName = classOf[JdiProxy].getName
    val proxySpecialMethods = Scala.dynamicTraitMethods ++ List("$eq$eq", "$bang$eq", "$plus", "apply", primitiveValueOfProxyMethodName)
    val proxyGenericApplyMethodName = "applyWithGenericType"

    def ArrayJdiProxy(typeName: String) = s"ArrayJdiProxy[$typeName]"

    /** JdiContext in all variants */
    val contextName = classOf[JdiContext].getSimpleName
    val contextFullName = classOf[JdiContext].getName

    /** Name of placeholder method, used in reflective compilation. */
    val placeholderName = "placeholder"

    /** Name of placeholder method for nested method, used in reflective compilation. */
    val placeholderNestedMethodName = "placeholderNestedMethod"

    /** Name of placeholder function method, used in reflective compilation. */
    val placeholderPartialFunctionName = "placeholderPartialFunction"

    /** Name of placeholder partial function method, used in reflective compilation. */
    val placeholderFunctionName = "placeholderFunction"

    /** Name of placeholder function for obtaining arguments types */
    val placeholderArgsName = "placeholderArgs"

    /** Name of placeholder function for handling `super` */
    val placeholderSuperName = "placeholderSuper"

    /** Name of proxy method, used in reflective compilation. */
    val proxyMethodName = "proxy"

    /** Name of proxy method, used in reflective compilation. */
    val valueProxyMethodName = "valueProxy"

    /** Name of proxy method, used in reflective compilation. */
    val objectOrStaticCallProxyMethodName = "objectOrStaticCallProxy"

    /** Name of proxy method, used in reflective compilation. */
    val classOfProxyMethodName = "classOfProxy"

    /** Name of stringify method, used in reflective compilation. */
    val stringifyMethodName = "stringify"

    /** Name of `isInstanceOf` method, used in reflective compilation. */
    val isInstanceOfMethodName = "isInstanceOfCheck"

    /** Name of generateHashCode method, used in reflective compilation. */
    val hashCodeMethodName = "generateHashCode"

    /** Name of method for setting local variables values, used in reflective compilation. */
    val setLocalVariable = "setLocalVariable"

    /** Name of context val on top level function for expression. */
    val contextParamName = "__context"

    /** Name of `this` stub. */
    val thisValName = "__this"

    /** Name of this proxy method, used in reflective compilation. */
    val thisObjectProxyMethodName = "thisObjectProxy"

    /** Name of invoke method method. */
    val invokeMethodName = "invokeMethod"

    /** Name of constructor method */
    val newInstance = "newInstance"

    val newClassContextName = "newClassContext"

    val proxyContextName = "proxyContextParam"

    val boxedProxiesNames = Seq(
      classOf[BooleanJdiProxy],
      classOf[ByteJdiProxy],
      classOf[CharJdiProxy],
      classOf[DoubleJdiProxy],
      classOf[FloatJdiProxy],
      classOf[IntJdiProxy],
      classOf[LongJdiProxy],
      classOf[ShortJdiProxy])
      .map(_.getSimpleName)

    val newClassName = "CustomFunction"

    val objectProxyForFieldMethodName = "objectProxyForField"
  }

}
