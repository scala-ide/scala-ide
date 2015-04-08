/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies

import org.scalaide.debug.internal.expression.JavaStaticInvocationProblem
import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.primitives.BooleanJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.UnitJdiProxy
import com.sun.jdi.ClassType
import com.sun.jdi.InterfaceType
import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType

/**
 * Implementation of [[org.scalaide.debug.internal.expression.proxies.JdiProxy]] for Java static calls.
 * They can be class or interface type (not instances) so there's no __value object reference for them.
 * There's also the third type - array type - but arrays don't have static methods or fields.
 */
trait StaticCallJdiProxy[ValueType <: ReferenceType] extends JdiProxy {

  protected def referenceType: ValueType = super.__type.asInstanceOf[ValueType]

  // we expect that it will be never called
  def __value: ObjectReference =
    throw JavaStaticInvocationProblem("Cannot get an underlying object reference - Java static calls don't use such reference")

  /** Implementation of field selection. */
  override def selectDynamic(name: String): JdiProxy =
    __context.getJavaStaticField[JdiProxy](referenceType, name)

  /** Forwards equality to debugged jvm. Invocation of this method shouldn't happen as Toolbox should report an error earlier. */
  override def ==(other: JdiProxy): BooleanJdiProxy =
    throw JavaStaticInvocationProblem("Comparison of two proxies for types used for static calls has no sense")
}

case class StaticCallClassJdiProxy(override val __context: JdiContext, override val referenceType: ClassType)
    extends StaticCallJdiProxy[ClassType] {

  /** Implementation of method application. */
  override def applyDynamic(name: String)(args: Any*): JdiProxy =
    __context.invokeJavaStaticMethod[JdiProxy](referenceType, name, args.map(_.asInstanceOf[JdiProxy]))

  /** Implementation of variable mutation. */
  override def updateDynamic(name: String)(value: Any): UnitJdiProxy =
    __context.setJavaStaticField(referenceType, name, value.asInstanceOf[JdiProxy])
}

case class StaticCallInterfaceJdiProxy(override val __context: JdiContext, override val referenceType: InterfaceType)
    extends StaticCallJdiProxy[InterfaceType] {

  /** Implementation of method application. Invocation of this method shouldn't happen as Toolbox should report an error earlier. */
  override def applyDynamic(name: String)(args: Any*): JdiProxy =
    throw JavaStaticInvocationProblem("Cannot invoke static method on interface as interfaces can't have static methods")

  /** Implementation of variable mutation. */
  override def updateDynamic(name: String)(value: Any): UnitJdiProxy =
    throw JavaStaticInvocationProblem("Cannot modify static field of interface. All interface fields are final")
}
