/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context.invoker

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy

import com.sun.jdi.Value

/**
 * Checks which calls can be potential access to Java field and tries to apply them.
 */
case class JavaField(proxy: JdiProxy, name: String, methodArgs: Seq[JdiProxy], context: JdiContext)
    extends MethodInvoker {

  final def apply(): Option[Value] = methodArgs match {
    case Seq() => tryGetValue()
    case Seq(newValue) if name.endsWith("_=") =>
      // this case is not tested because, when it really could be used, Toolbox throws exception earlier
      val fieldName = name.dropRight(2) // drop _= at the end of Scala setter
      trySetValue(fieldName, newValue.__underlying)
    case _ => None
  }

  private def refType = proxy.__underlying.referenceType()

  private def tryGetValue() = {
    val field = Option(refType.fieldByName(name))
    field map proxy.__underlying.getValue
  }

  private def trySetValue(fieldName: String, newValue: Value) = {
    val field = Option(refType.fieldByName(fieldName))
    field.map { f =>
      proxy.__underlying.setValue(f, newValue)
      context.mirrorOf(())
    }
  }
}