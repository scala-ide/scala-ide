/*
 * Copyright (c) 2014 Contributor. All rights reserved.
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

      val String = classOf[java.lang.String].getName

      val Boolean = classOf[java.lang.Boolean].getName

      val Integer = classOf[java.lang.Integer].getName

      val Double = classOf[java.lang.Double].getName

      val Float = classOf[java.lang.Float].getName

      val Character = classOf[java.lang.Character].getName

      val Long = classOf[java.lang.Long].getName

      val Byte = classOf[java.lang.Byte].getName

      val Short = classOf[java.lang.Short].getName

      val all = Set(Integer, Double, Float, Long, Character, Boolean, Byte, Short, Unit)
    }

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

    val rangeInclusive = "scala.collection.immutable.Range$Inclusive"
    val range = "scala.collection.immutable.Range"

    /**
     * Regex for matching Scala arrays.
     * Matches both `Array[A]` and `scala.Array[A]` and extracts A to group.
     */
    val Array = """(?:scala\.)?Array\[(.+)\]""".r

    def Array(typeName: String) = s"scala.Array[$typeName]"

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

    /** Prefix for object or type used for static call in generated stubs. */
    val objectOrStaticCallTypeNamePrefix = "$o_"

    /** Type used to show for custom user-defined lambdas */
    val lambdaType = "<custom_lambda>"

    /** Matches names that starts with objNamePrefix and extracts name without prefix. */
    object PrefixedObjectOrStaticCall {
      def unapply(name: String): Option[String] =
        if (name.startsWith(objectOrStaticCallTypeNamePrefix)) Some(name.drop(objectOrStaticCallTypeNamePrefix.length))
        else None
    }

    /*  JdiProxy - in all variants */
    val proxyName = classOf[JdiProxy].getSimpleName
    val proxyFullName = classOf[JdiProxy].getName
    val proxyObjectOrStaticCallFullName = JdiContext.toObjectOrStaticCall(proxyFullName)

    def ArrayJdiProxy(typeName: String) = s"ArrayJdiProxy[$typeName]"

    /* JdiContext in all variants */
    val contextName = classOf[JdiContext].getSimpleName
    val contextFullName = classOf[JdiContext].getName
    val contextObjectOrStaticCallFullName = JdiContext.toObjectOrStaticCall(contextFullName)

    /** Name of placeholder method, used in reflective compilation. */
    val placeholderName = "placeholder"

    /** Name of placeholder function method, used in reflective compilation. */
    val placeholderPartialFunctionName = "placeholderPartialFunction"

    /** Name of placeholder partial function method, used in reflective compilation. */
    val placeholderFunctionName = "placeholderFunction"

    /** Name of placeholder function for obtaining arguments types */
    val placeholderArgsName = "placeholderArgs"

    /** Name of proxy method, used in reflective compilation. */
    val proxyMethodName = "proxy"

    /** Name of proxy method, used in reflective compilation. */
    val valueProxyMethodName = "valueProxy"

    /** Name of proxy method, used in reflective compilation. */
    val objectOrStaticCallProxyMethodName = "objectOrStaticCallProxy"

    /** Name of stringify method, used in reflective compilation. */
    val stringifyMethodName = "stringify"

    /** Name of generateHashCode method, used in reflective compilation. */
    val hashCodeMethodName = "generateHashCode"

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

    val primitiveValueOfProxyMethodName = "__value"

    val newClassName = "CustomFunction"

    val customLambdaPrefix = "FunctionNo"
  }

}
