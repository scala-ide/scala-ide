/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context.invoker

import scala.collection.JavaConversions._

import org.scalaide.debug.internal.expression.Arity
import org.scalaide.debug.internal.expression.SimpleInvokeOnClassType
import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy

import com.sun.jdi.ArrayType
import com.sun.jdi.ClassType
import com.sun.jdi.Method
import com.sun.jdi.Value

/**
 * Calls standard static Java methods in context of debug.
 */
class JavaStaticMethod(val referenceType: ClassType, val methodName: String, val args: Seq[JdiProxy], context: JdiContext)
    extends BaseMethodInvoker {

  override def apply(): Option[Value] = {
    def invoke(method: Method): Value =
      referenceType.invokeMethod(context.currentThread(), method, generateArguments(method))

    handleMultipleOverloads(matching.filter(_.isStatic), invoke)
  }
}

/**
 * Calls vararg static Java methods in context of debug.
 */
class JavaStaticVarArgMethod(val referenceType: ClassType, val methodName: String, val args: Seq[JdiProxy], val context: JdiContext)
    extends BaseMethodInvoker
    with JavaVarArgSupport {

  override def apply(): Option[Value] = {
    def invoke(method: Method): Value = {
      val normalSize = method.arity - 1
      val standardArgs = generateArguments(method).take(normalSize)
      val varArgs = packToJavaVarArg(method.argumentTypes.last.asInstanceOf[ArrayType], args.drop(normalSize))
      referenceType.invokeMethod(context.currentThread(), method, standardArgs :+ varArgs)
    }

    handleMultipleOverloads(candidates.filter(_.isStatic), invoke)
  }
}
