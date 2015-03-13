/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context.invoker

import scala.collection.JavaConversions._

import org.scalaide.debug.internal.expression.Arity
import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy

import com.sun.jdi.ArrayType
import com.sun.jdi.Method
import com.sun.jdi.Value

/**
 * Support for Java-style varargs (with Array[A] as last argument).
 *
 * Extends `VarArgSupport` with:
 * * `candidates` - sequence of methods that are a vararg ones
 * * `packToJavaVarArg` which packs seq of JdiProxies to ArrayReference
 */
trait JavaVarArgSupport
    extends VarArgSupport {
  self: BaseMethodInvoker =>

  protected def packToJavaVarArg(tpe: ArrayType, proxies: Seq[JdiProxy]): Value = {
    val seqResult = packToVarArg(proxies)

    val array = tpe.newInstance(proxies.size)
    val copyToArrayMethod = context.methodOn(seqResult, "copyToArray", arity = 1)
    seqResult.invokeMethod(context.currentThread(), copyToArrayMethod, List(array))
    array
  }

  protected def candidates: Seq[Method] = allMethods.filter(_.isVarArgs)

}

/**
 * Calls vararg method on given `ObjectReference` in context of debug.
 */
class JavaVarArgMethod(proxy: JdiProxy, val methodName: String, val args: Seq[JdiProxy], protected val context: JdiContext)
    extends BaseMethodInvoker
    with JavaVarArgSupport {

  def referenceType = proxy.referenceType

  override def apply(): Option[Value] = {

    def invoke(method: Method): Value = {
      val normalSize = method.arity - 1
      val standardArgs = generateArguments(method).take(normalSize)
      val varArgs = packToJavaVarArg(method.argumentTypes.last.asInstanceOf[ArrayType], args.drop(normalSize))
      proxy.__underlying.invokeMethod(context.currentThread(), method, standardArgs :+ varArgs)
    }

    handleMultipleOverloads(candidates, invoke)
  }
}
