/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal

import scala.language.implicitConversions

import com.sun.jdi.Method
import com.sun.jdi.ClassType
import com.sun.jdi.ObjectReference

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
 * [[org.scalaide.debug.internal.expression.NewTypesContext]] contains append only mutable state about types encountered
 * during transformation, it's updated by multiple transformation phases and passed over.
 *
 * Several helpers exists here also:
 * $ [[org.scalaide.debug.internal.expression.Names]] contains strings with names of Java and Scala types used in
 * reflective compilation as well as some debugger-specific names.
 *
 * Special names used by debugger resides in [[org.scalaide.debug.internal.expression.DebuggerSpecific]].
 */
package object expression extends JdiHelpers {

  import JdiHelpers._

  implicit def Arity(method: Method): Arity = new Arity(method)

  implicit def SimpleInvokeOnClassType(ref: ClassType): SimpleInvokeOnClassType = new SimpleInvokeOnClassType(ref)

  implicit def SimpleInvokeOnObjectRef(ref: ObjectReference): SimpleInvokeOnObjectRef = new SimpleInvokeOnObjectRef(ref)
}
