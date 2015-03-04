/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal

import scala.collection.JavaConversions._

import com.sun.jdi.Method
import com.sun.jdi.ClassType
import com.sun.jdi.ObjectReference
import com.sun.jdi.ThreadReference
import com.sun.jdi.Value

/**
 * Main entry point into expression evaluation in Scala IDE debugger.
 *
 * To evaluate expression see [[org.scalaide.debug.internal.expression.ExpressionManager]] object, which
 * is initialized from [[org.scalaide.debug.internal.ScalaDebugger]] during debug and takes care of holding
 * debug session state, and evaluating expressions in GUI friendly way.
 *
 * For actual implementation of expression evaluation see [[org.scalaide.debug.internal.expression.JdiExpressionEvaluator]]
 * which is an JDI implementation of [[org.scalaide.debug.internal.expression.ExpressionEvaluator]].
 *
 * In [[org.scalaide.debug.internal.expression.ConditionManager]] sits the logic for evaluating conditions for
 * conditional expressions.
 *
 * [[org.scalaide.debug.internal.expression.TypesContext]] contains append only mutable state about types encountered
 * during transformation, it's updated by multiple transformation phases and passed over.
 *
 * Several helpers exists here also:
 * $ [[org.scalaide.debug.internal.expression.Names]] contains strings with names of Java and Scala types used in
 * reflective compilation as well as some debugger-specific names.
 *
 * Special names used by debugger resides in [[org.scalaide.debug.internal.expression.DebuggerSpecific]].
 */
package object expression {

  /**
   * Used when you wan to do something side-effecting with result of function - like log it's result.
   * This way one does not have to create a val, assign result to it, log and return.
   */
  implicit final class OnSide[A](private val a: A) extends AnyVal {
    def onSide(f: A => Unit): A = { f(a); a }
  }

  implicit final class Arity(private val method: Method) extends AnyVal {
    def arity: Int = method.arguments.size
  }

  implicit final class SimpleInvokeOnClassType(ref: ClassType) {
    def invokeMethod(threadRef: ThreadReference, method: Method, args: Seq[Value]): Value =
      ref.invokeMethod(threadRef, method, args, ObjectReference.INVOKE_SINGLE_THREADED)
    def newInstance(threadRef: ThreadReference, method: Method, args: Seq[Value]): ObjectReference =
      ref.newInstance(threadRef, method, args, ObjectReference.INVOKE_SINGLE_THREADED)
  }

  implicit final class SimpleInvokeOnObjectRef(ref: ObjectReference) {
    def invokeMethod(threadRef: ThreadReference, method: Method, args: Seq[Value]): Value =
      ref.invokeMethod(threadRef, method, args, ObjectReference.INVOKE_SINGLE_THREADED)
  }

  /** Helper method for Tuple2 operations */
  final def first[A, B](t: (A, B)): A = t._1

  /** Helper method for Tuple2 operations */
  final def second[A, B](t: (A, B)): B = t._2

  /**
   * Prints `com.sun.jdi.Method` in human-readable format. Example:
   * {{{
   * def apply(xs: scala.collection.Seq): scala.collection.immutable.List (defined in: scala.collection.immutable.List$, line(s): [457])
   * }}}
   */
  final def prettyPrint(method: Method): String = util.Try {
    def param(f: Method => Boolean, name: String): String = if (f(method)) name else ""

    def args = util.Try {
      method.arguments.map(arg => arg.name + ": " + arg.typeName).mkString("(", ",", ")")
    }.toOption.getOrElse("(<no info>)")

    param(_.isPrivate, "private ") +
      param(_.isProtected, "protected ") +
      param(_.isFinal, "final ") +
      param(_.isAbstract, "abstract ") +
      "def " +
      method.name +
      args +
      ": " + method.returnType +
      " (" +
      "defined in: " + method.declaringType +
      ", line(s): " + method.allLineLocations.map(_.lineNumber).mkString("[", ",", "]") +
      ")"
  }.toOption.getOrElse("<no information>")
}