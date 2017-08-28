/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context.invoker

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import com.sun.jdi.StackFrame
import com.sun.jdi.Value
import com.sun.jdi.InvalidStackFrameException

/**
 * Invoker for setting values to local (to frame) variables.
 */
class LocalVariableInvoker(
    variableName: String,
    newValue: JdiProxy,
    context: JdiContext,
    currentFrame: () => StackFrame) extends MethodInvoker {

  /** Returns `None` if value is not found */
  override final def apply(): Option[Value] = try {
    val frame = currentFrame()
    for {
      variable <- Option(frame.visibleVariableByName(variableName))
      result = frame.setValue(variable, autobox(variable.`type`, newValue))
    } yield context.mirrorOf(result)
  } catch {
    case _: InvalidStackFrameException =>
      // TODO - O-8559 - assignment to local var of primitive type does not work now
      throw new UnsupportedFeature("assignment to local var of primitive type")
  }
}
