/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context.invoker

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy

import com.sun.jdi.ClassType
import com.sun.jdi.ReferenceType
import com.sun.jdi.Value

abstract class JavaStaticField {
  protected val referenceType: ReferenceType
  protected val fieldName: String

  protected val field = Option(referenceType.fieldByName(fieldName))
}

class JavaStaticFieldGetter(val referenceType: ReferenceType, val fieldName: String)
    extends JavaStaticField
    with MethodInvoker {

  def apply(): Option[Value] = field.map(referenceType.getValue)
}

class JavaStaticFieldSetter(val referenceType: ClassType, val fieldName: String, newValue: JdiProxy, context: JdiContext)
    extends JavaStaticField
    with MethodInvoker {

  def apply(): Option[Value] = field.map { f =>
    context.mirrorOf(
      referenceType.setValue(f, newValue.__value))
  }
}
