/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context.invoker

import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy

import com.sun.jdi.ClassType
import com.sun.jdi.Method
import com.sun.jdi.Value

// provides support for constructors
// TODO - support for Scala and Java varargs
class StandardConstructor(className: String, val args: Seq[JdiProxy], context: JdiContext)
    extends BaseMethodInvoker {
  override def referenceType: ClassType = context.classByName(className)

  override val methodName: String = Scala.constructorMethodName

  override def apply(): Option[Value] = {
    def invoke(method: Method): Value = {
      val finalArgs = generateArguments(method)
      referenceType.newInstance(context.currentThread(), method, finalArgs)
    }

    matching match {
      case Nil => None
      case single +: Nil => Some(invoke(single))
      case multiple =>
        logger.warn(multipleOverloadsMessage(multiple))
        Some(invoke(multiple.head))
    }
  }
}