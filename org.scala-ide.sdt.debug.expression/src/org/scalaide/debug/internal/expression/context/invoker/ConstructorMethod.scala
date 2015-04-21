/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context.invoker

import scala.util.Try

import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.SimpleInvokeOnClassType
import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy

import com.sun.jdi.ClassType
import com.sun.jdi.Method
import com.sun.jdi.Value

abstract class ConstructorMethod(className: String, context: JdiContext) extends BaseMethodInvoker {
  override def referenceType: ClassType = context.classByName(className)
  override def methodName: String = Scala.constructorMethodName
}

class StandardConstructorMethod(className: String, val args: Seq[JdiProxy], context: JdiContext)
    extends ConstructorMethod(className, context) {

  override def apply(): Option[Value] = {
    def invoke(method: Method): Option[Value] = {
      val finalArgs = generateArguments(method)
      // ignore any errors from method invocation - it can mean it's a var-arg constructor
      Try(referenceType.newInstance(context.currentThread(), method, finalArgs)).toOption
    }

    matching match {
      case Nil => None
      case single +: Nil => invoke(single)
      case multiple =>
        logger.warn(multipleOverloadsMessage(multiple))
        invoke(multiple.head)
    }
  }
}

class VarArgConstructorMethod(className: String, val args: Seq[JdiProxy], protected val context: JdiContext)
    extends ConstructorMethod(className, context)
    with ScalaVarArgSupport {

  override def apply(): Option[Value] = {
    val varargMethods = candidates.flatMap(matchesVarArgSig)

    def invoke(method: Method, normalSize: Int): Value = {
      val standardArgs = generateArguments(method).take(normalSize)
      val varArgs = packToVarArg(args.drop(normalSize))
      referenceType.newInstance(context.currentThread(), method, standardArgs :+ varArgs)
    }

    varargMethods match {
      case Nil => None
      case (method, normalSize) +: Nil =>
        Some(invoke(method, normalSize))
      case multiple =>
        logger.warn(multipleOverloadsMessage(multiple.map(first)))
        Some((invoke _).tupled(multiple.head))
    }
  }
}

class JavaVarArgConstructorMethod(className: String, val args: Seq[JdiProxy], context: JdiContext)
    extends ConstructorMethod(className, context) {
  // with JavaVarArgSupport {

  override def apply(): Option[Value] = {
    // TODO - logic for java varargs
    ???
  }
}