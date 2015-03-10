/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context.invoker

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy

import com.sun.jdi.Field
import com.sun.jdi.Value

/**
 * Checks which calls can be potential access to Java field and tries to apply them.
 */
case class JavaField(proxy: JdiProxy, name: String, methodArgs: Seq[JdiProxy], context: JdiContext)
    extends MethodInvoker {

  final def apply(): Option[Value] = (name, methodArgs) match {
    case ScalaGetter(field) =>
      Some(proxy.__underlying.getValue(field))
    // this case is not tested because, when it really could be used, Toolbox throws exception earlier
    case ScalaSetter(field, newValue) =>
      proxy.__underlying.setValue(field, newValue)
      Some(context.mirrorOf(()))
    case _ => None
  }

  private def refType = proxy.referenceType

  private object ScalaSetter {
    def unapply(t: (String, Seq[JdiProxy])): Option[(Field, Value)] = {
      val (methodName, newValues) = t
      newValues match {
        // drop _= at the end of Scala setter
        case Seq(argProxy) if methodName.endsWith("_=") =>
          val fieldName = methodName.dropRight(2)
          Option(refType.fieldByName(fieldName)).map(
            field => (field, getValue(field.`type`, argProxy)))
        case _ => None
      }
    }
  }

  private object ScalaGetter {
    def unapply(t: (String, Seq[JdiProxy])): Option[Field] = {
      val (methodName, newValues) = t
      if (newValues.isEmpty) Option(refType.fieldByName(methodName)) else None
    }
  }

}