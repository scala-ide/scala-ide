/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import scala.collection.JavaConversions._

import com.sun.jdi.Method
import com.sun.jdi.ClassType
import com.sun.jdi.ObjectReference
import com.sun.jdi.ThreadReference
import com.sun.jdi.Value

/**
 * Contains some helpers to ease work with JDI from Scala.
 *
 * Classes are defined here to enable usage of `AnyVal`.
 */
object JdiHelpers {

  final class Arity(private val method: Method) extends AnyVal {
    def arity: Int = method.argumentTypeNames.size
  }

  final class SimpleInvokeOnClassType(private val ref: ClassType) extends AnyVal {
    def invokeMethod(threadRef: ThreadReference, method: Method, args: Seq[Value]): Value =
      ref.invokeMethod(threadRef, method, args, ObjectReference.INVOKE_SINGLE_THREADED)
    def newInstance(threadRef: ThreadReference, method: Method, args: Seq[Value]): ObjectReference =
      ref.newInstance(threadRef, method, args, ObjectReference.INVOKE_SINGLE_THREADED)
  }

  final class SimpleInvokeOnObjectRef(private val ref: ObjectReference) extends AnyVal {
    def invokeMethod(threadRef: ThreadReference, method: Method, args: Seq[Value]): Value =
      ref.invokeMethod(threadRef, method, args, ObjectReference.INVOKE_SINGLE_THREADED)
  }
}

/**
 * Contains some helpers to ease work with JDI from Scala.
 */
trait JdiHelpers {

  /**
   * Prints `com.sun.jdi.Method` in human-readable format. Example:
   * {{{
   * def apply(xs: scala.collection.Seq): scala.collection.immutable.List (defined in: scala.collection.immutable.List$, line(s): [457])
   * }}}
   */
  final def prettyPrint(method: Method): String = {
    def err[A](f: => A) = util.Try(f).toOption.getOrElse("<no info>")
    def param(f: Method => Boolean, name: String): String = if (f(method)) name else ""

    val params = err(Seq(
      param(_.isPrivate, "private"),
      param(_.isProtected, "protected"),
      param(_.isFinal, "final"),
      param(_.isAbstract, "abstract")).mkString(" "))

    val name = err(method.name)
    val args = err {
      method.arguments.map(arg => arg.name + ": " + arg.typeName).mkString(",")
    }
    val returnType = err(method.returnType)
    val declaringType = err(method.declaringType)
    val lineLocations = err(method.allLineLocations.map(_.lineNumber).mkString(","))

    s"$params def $name($args): $returnType (defined in: $declaringType, line(s): [$lineLocations])"
  }
}
