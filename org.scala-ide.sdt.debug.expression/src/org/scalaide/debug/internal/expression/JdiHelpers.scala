/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import scala.collection.JavaConverters._

import com.sun.jdi.Method
import com.sun.jdi.ClassType
import com.sun.jdi.ObjectReference
import com.sun.jdi.StackFrame
import com.sun.jdi.ThreadReference
import com.sun.jdi.Value

import org.eclipse.jdi.internal.LocalVariableImpl

/**
 * Contains some helpers to ease work with JDI from Scala.
 *
 * Classes are defined here to enable usage of `AnyVal`.
 */
object JdiHelpers {

  final class Arity(private val method: Method) extends AnyVal {
    def arity: Int = method.argumentTypeNames.size
  }

  private def methodInvocationFlags(method: Method): Int = {
    val baseFlags = ObjectReference.INVOKE_SINGLE_THREADED

    if (method.isAbstract || method.isConstructor) baseFlags
    else baseFlags | ObjectReference.INVOKE_NONVIRTUAL
  }

  final class SimpleInvokeOnClassType(private val ref: ClassType) extends AnyVal {
    def invokeMethod(threadRef: ThreadReference, method: Method, args: Seq[Value]): Value =
      ref.invokeMethod(threadRef, method, args.asJava, methodInvocationFlags(method))
    def newInstance(threadRef: ThreadReference, method: Method, args: Seq[Value]): ObjectReference =
      ref.newInstance(threadRef, method, args.asJava, methodInvocationFlags(method))
  }

  final class SimpleInvokeOnObjectRef(private val ref: ObjectReference) extends AnyVal {
    def invokeMethod(threadRef: ThreadReference, method: Method, args: Seq[Value]): Value =
      ref.invokeMethod(threadRef, method, args.asJava, methodInvocationFlags(method))
  }

  /**
   * Gets this object reference from current stack frame. If `thisObject()` returns `null`
   * for given frame it tries to find out `thisObject` of method reference type in underlying
   * frames.
   */
  def thisObject(current: StackFrame): Option[ObjectReference] = {
    Option(current.thisObject).orElse {
      import scala.collection.JavaConverters._
      current.visibleVariables.asScala.collectFirst {
        case lambda: LocalVariableImpl =>
          lambda.method.referenceTypeImpl
      }.flatMap { refType =>
        current.thread.frames.asScala.collectFirst {
          case f if (f.thisObject ne null) && (f.thisObject.referenceType == refType) =>
            f.thisObject
        }
      }
    }
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
    def err[A](f: => A): Any = util.Try(f).toOption.getOrElse("<no info>")
    def param(f: Method => Boolean, name: String): String = if (f(method)) name else ""

    val params = err(Seq(
      param(_.isPrivate, "private"),
      param(_.isProtected, "protected"),
      param(_.isFinal, "final"),
      param(_.isAbstract, "abstract")).mkString(" "))

    val name = err(method.name)
    val args = err {
      method.arguments.asScala.map(arg => arg.name + ": " + arg.typeName).mkString(",")
    }
    val returnType = err(method.returnType)
    val declaringType = err(method.declaringType)
    val lineLocations = err(method.allLineLocations.asScala.map(_.lineNumber).mkString(","))

    s"$params def $name($args): $returnType (defined in: $declaringType, line(s): [$lineLocations])"
  }
}
