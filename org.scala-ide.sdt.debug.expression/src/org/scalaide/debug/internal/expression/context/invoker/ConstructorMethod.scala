/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context.invoker

import scala.collection.JavaConversions._
import scala.util.Try

import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.SimpleInvokeOnClassType
import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy

import com.sun.jdi.ArrayType
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
    def invoke(method: Method): Value = {
      val finalArgs = generateArguments(method)
      referenceType.newInstance(context.currentThread(), method, finalArgs)
    }

    // ignore any errors from method invocation - it can mean it's a var-arg constructor
    Try(handleMultipleOverloads(matching, invoke)).toOption.flatten
  }
}

class VarArgConstructorMethod(className: String, val args: Seq[JdiProxy], protected val context: JdiContext)
    extends ConstructorMethod(className, context)
    with ScalaVarArgSupport {

  override def apply(): Option[Value] = {
    def invoke(method: Method): Value = {
      val normalSize = method.arity - 1
      val standardArgs = generateArguments(method).take(normalSize)
      val varArgs = packToVarArg(args.drop(normalSize))
      referenceType.newInstance(context.currentThread(), method, standardArgs :+ varArgs)
    }

    Try(handleMultipleOverloads(candidates, invoke)).toOption.flatten
  }
}

class JavaVarArgConstructorMethod(className: String, val args: Seq[JdiProxy], protected val context: JdiContext)
    extends ConstructorMethod(className, context)
    with JavaVarArgSupport {

  override def apply(): Option[Value] = {
    def invoke(method: Method): Value = {
      val normalSize = method.arity - 1
      val standardArgs = generateArguments(method).take(normalSize)
      val varArgs = packToJavaVarArg(method.argumentTypes.last.asInstanceOf[ArrayType], args.drop(normalSize))
      referenceType.newInstance(context.currentThread(), method, standardArgs :+ varArgs)
    }

    Try(handleMultipleOverloads(candidates, invoke)).toOption.flatten
  }
}
