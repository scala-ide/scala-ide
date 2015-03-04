/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context.invoker

import scala.reflect.NameTransformer

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.JdiProxy

import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType
import com.sun.jdi.Type
import com.sun.jdi.Value

/**
 * Calls vararg method on given `ObjectReference` in context of debug.
 */
class VarArgMethod(proxy: JdiProxy, name: String, val args: Seq[JdiProxy], context: JdiContext)
    extends BaseMethodInvoker {

  override val methodName: String = NameTransformer.encode(name)

  override def referenceType: ReferenceType = proxy.referenceType

  private val seqName = "scala.collection.Seq"

  private def seqObjectRef: ObjectReference = context.objectByName(seqName)

  private def emptyListRef: ObjectReference = {
    def emptyMethod = context.methodOn(seqObjectRef, "empty")
    seqObjectRef.invokeMethod(context.currentThread(), emptyMethod, List[Value]()).asInstanceOf[ObjectReference]
  }

  private def packToVarArg(proxies: Seq[JdiProxy]): Value = {
    def addMethod = context.methodOn(seqName, "$plus$colon")
    def canBuildFrom = seqObjectRef.invokeMethod(context.currentThread(), context.methodOn(seqObjectRef, "canBuildFrom"), List[Value]())

    proxies.foldRight(emptyListRef) {
      (proxy, current) =>
        current.invokeMethod(context.currentThread(), addMethod, List(proxy.__underlying, canBuildFrom)).asInstanceOf[ObjectReference]
    }
  }

  // we have to add `1` as someone can call vararg without any arguments at all
  private def candidates = allMethods.filter(_.arity <= args.size + 1)

  private object PossiblyVarArg {
    def unapply(types: Seq[Type]): Option[Seq[Type]] = types match {
      case normal :+ vararg if vararg.name == seqName && checkTypes(normal) => Some(normal)
      case _ => None
    }
  }

  private def matchesVarArgSig(method: Method): Option[(Method, Int)] =
    argumentTypesLoaded(method, proxy.proxyContext) match {
      case PossiblyVarArg(normal) => Some(method, normal.size)
      case _ => None
    }

  override def apply(): Option[Value] = {
    val varargMethods = candidates.flatMap(matchesVarArgSig)

    def invoke(method: Method, normalSize: Int): Value = {
      val standardArgs = generateArguments(method).take(normalSize)
      val varArgs = packToVarArg(args.drop(normalSize))
      proxy.__underlying.invokeMethod(context.currentThread(), method, standardArgs :+ varArgs)
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
