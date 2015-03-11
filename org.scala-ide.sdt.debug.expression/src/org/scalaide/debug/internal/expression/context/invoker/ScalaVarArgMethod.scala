/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context.invoker

import org.scalaide.debug.internal.expression.Arity
import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy

import com.sun.jdi.Method
import com.sun.jdi.Type
import com.sun.jdi.Value

/**
 * Support for Scala-style varargs (with Seq[A] as last argument).
 *
 * Extends `VarArgSupport` with `candidates` - sequence of methods that could be a vararg ones.
 */
trait ScalaVarArgSupport
    extends VarArgSupport {
  self: BaseMethodInvoker =>

  private object PossiblyVarArg {
    def unapply(types: Seq[Type]): Boolean = types match {
      case normal :+ vararg if vararg.name == seqName && checkTypes(normal) => true
      case _ => false
    }
  }

  private def matchesVarArgSig(method: Method): Option[Method] =
    argumentTypesLoaded(method, context) match {
      case PossiblyVarArg() => Some(method)
      case _ => None
    }

  // we have to add `1` as someone can call vararg without any arguments at all
  protected def candidates: Seq[Method] =
    allMethods.filter(_.arity <= args.size + 1).flatMap(matchesVarArgSig)

}

/**
 * Calls vararg method on given `ObjectReference` in context of debug.
 */
class ScalaVarArgMethod(proxy: JdiProxy, name: String, val args: Seq[JdiProxy], protected val context: JdiContext)
    extends ScalaMethod(name, proxy)
    with ScalaVarArgSupport {

  override def apply(): Option[Value] = {
    def invoke(method: Method): Value = {
      val normalSize = method.arity - 1
      val standardArgs = generateArguments(method).take(normalSize)
      val varArgs = packToVarArg(args.drop(normalSize))
      proxy.__underlying.invokeMethod(context.currentThread(), method, standardArgs :+ varArgs)
    }

    handleMultipleOverloads(candidates, invoke)
  }
}
