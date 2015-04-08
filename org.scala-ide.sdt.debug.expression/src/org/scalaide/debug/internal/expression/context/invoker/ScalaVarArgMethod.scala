/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context.invoker

import scala.collection.JavaConversions._

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.expression.proxies.ObjectJdiProxy

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

  private def isSeq(tpe: Type) = tpe.name == Names.Scala.seq

  private def isVarArgSig(method: Method) =
    isFirstParamVarArg(method) || isLastParamVarArg(method)

  protected def isFirstParamVarArg(method: Method) = argumentTypesLoaded(method, context) match {
    case vararg +: normal if isSeq(vararg) && checkTypesRight(normal) => true
    case _ => false
  }

  private def isLastParamVarArg(method: Method) = argumentTypesLoaded(method, context) match {
    case normal :+ vararg if isSeq(vararg) && checkTypes(normal) => true
    case _ => false
  }

  // we have to add `1` as someone can call vararg without any arguments at all
  protected def candidates: Seq[Method] =
    allMethods.filter(_.arity <= args.size + 1).filter(isVarArgSig)
}

/**
 * Calls vararg method on given `ObjectReference` in context of debug.
 */
class ScalaVarArgMethod(proxy: ObjectJdiProxy, name: String, val args: Seq[JdiProxy], protected val context: JdiContext)
    extends ScalaMethod(name, proxy)
    with ScalaVarArgSupport {

  override def apply(): Option[Value] = {
    def invoke(method: Method): Value = {
      val normalSize = method.arity - 1

      val argsWithVarArg = if (isFirstParamVarArg(method)) {
        val standardArgs = generateArgumentsRight(method).takeRight(normalSize)
        val varArgs = packToVarArg(args.dropRight(normalSize))
        varArgs +: standardArgs
      } else {
        val standardArgs = generateArguments(method).take(normalSize)
        val varArgs = packToVarArg(args.drop(normalSize))
        standardArgs :+ varArgs
      }

      proxy.__value.invokeMethod(context.currentThread(), method, argsWithVarArg)
    }

    handleMultipleOverloads(candidates, invoke)
  }
}
