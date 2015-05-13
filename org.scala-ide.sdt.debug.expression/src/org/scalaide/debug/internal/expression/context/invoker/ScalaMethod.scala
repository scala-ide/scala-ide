/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context.invoker

import scala.collection.JavaConversions._

import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.expression.proxies.ObjectJdiProxy

import com.sun.jdi._

/**
 * Implementation of `BaseMethodInvoker`for Scala methods.
 */
abstract class ScalaMethod(val realThisType: Option[String], proxy: ObjectJdiProxy)
    extends BaseMethodInvoker {

  protected override def referenceType: ReferenceType = proxy.__type

  protected override def methodArgs(method: Method): Seq[JdiProxy] =
    if (isMethodFromSuperTrait(method)) proxy +: args else args

  protected override def allMethods: Seq[Method] =
    (super.allMethods ++ additionalMethods.keySet)
      .filterNot(_.isAbstract)
      .sortBy(realThisType.isDefined && !isDeclaredOnThisType(_))

  protected def invokeMethod(threadRef: ThreadReference, method: Method, args: Seq[Value]): Value =
    additionalMethods.get(method).map { classType =>
      SimpleInvokeOnClassType(classType).invokeMethod(threadRef, method, args)
    } getOrElse {
      proxy.__value.invokeMethod(threadRef, method, args)
    }

  private def isMethodFromSuperTrait(method: Method): Boolean =
    !super.allMethods.contains(method)

  private def isDeclaredOnThisType(method: Method): Boolean = {
    def strip(s: String) = s.replace("$", ".").replace(".class", "")
    val declaredOn = strip(method.declaringType().name())
    realThisType.exists(tpe => declaredOn.endsWith(strip(tpe)))
  }

  private lazy val additionalMethods: Map[Method, ClassType] = {

    def getAssociatedClasses(tpe: String)(references: List[ReferenceType]) = references.collect {
      case cls: ClassType if cls.name.endsWith(tpe + "$class") => cls
    }

    val methodsWithClassType = for {
      tpe <- realThisType.toList
      classLoader <- Option(referenceType.classLoader).toList
      associatedClass <- getAssociatedClasses(tpe)(classLoader.visibleClasses.toList)
      method <- associatedClass.methods if isDeclaredOnThisType(method) && method.name == methodName
    } yield (method, associatedClass)

    methodsWithClassType.toMap
  }
}
