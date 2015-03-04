/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context.invoker

import org.scalaide.debug.internal.expression.proxies.JdiProxy

import com.sun.jdi.ClassType
import com.sun.jdi.ReferenceType
import com.sun.jdi.Value

abstract class JavaStaticField {
  protected val referenceType: ReferenceType
  protected val fieldName: String

  protected val field = {
    Option(referenceType.fieldByName(fieldName)).getOrElse {
      throw new NoSuchFieldError(s"type ${referenceType.name} has no static field named $fieldName")
    }
  }
}

class JavaStaticFieldGetter(val referenceType: ReferenceType, val fieldName: String) extends JavaStaticField {
  def getValue(): Value = referenceType.getValue(field)
}

class JavaStaticFieldSetter(val referenceType: ClassType, val fieldName: String) extends JavaStaticField {
  def setValue(newValue: Any): Unit = {
    val newValueProxy = newValue.asInstanceOf[JdiProxy].__underlying
    referenceType.setValue(field, newValueProxy)
  }
}