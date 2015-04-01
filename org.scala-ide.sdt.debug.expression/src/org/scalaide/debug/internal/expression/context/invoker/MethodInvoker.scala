/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context.invoker

import scala.collection.JavaConversions._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.ArrayJdiProxy
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.BoxedJdiProxy
import org.scalaide.logging.HasLogger

import com.sun.jdi.ArrayType
import com.sun.jdi.ClassNotLoadedException
import com.sun.jdi.ClassType
import com.sun.jdi.InterfaceType
import com.sun.jdi.Method
import com.sun.jdi.PrimitiveType
import com.sun.jdi.ReferenceType
import com.sun.jdi.Type
import com.sun.jdi.Value

/** Common interface for method invokers. */
trait MethodInvoker extends HasLogger {

  def apply(): Option[Value]

  /**
   * Checks if type underlying given proxy conforms to given type.
   *
   * @param proxy object to check
   * @param tpe type to check
   */
  protected final def conformsTo(proxy: JdiProxy, tpe: Type): Boolean = {
    if (proxy.__underlying == null) tpe.isInstanceOf[ClassType]
    else (tpe, proxy, proxy.referenceType) match {
      case (primitive: PrimitiveType, boxed: BoxedJdiProxy[_, _], _) =>
        primitive.name == boxed.primitiveName
      case (parentArrayType: ArrayType, _, thisArrayType: ArrayType) =>
        isSuperClassOf(parentArrayType.componentType)(thisArrayType.componentType)
      case (parentType: Type, _, thisType: Type) =>
        isSuperClassOf(parentType)(thisType)
    }
  }

  private def isSuperClassOf(parentType: Type)(thisType: Type): Boolean = {
    (parentType, thisType) match {
      case (parentType, thisType) if parentType == thisType || parentType.name == Names.Java.Object =>
        true
      case (parentInterfaceType: InterfaceType, thisClassType: ClassType) =>
        thisClassType.allInterfaces.contains(parentInterfaceType)
      case (parentRefType: ReferenceType, thisClassType: ClassType) =>
        Option(thisClassType.superclass()).map(isSuperClassOf(parentRefType)).getOrElse(false)
      case _ =>
        false
    }
  }

  /**
   * Runs `argumentTypes()` on given method, but catches all `ClassNotLoadedException`s and loads
   * required classes using provided context.
   * Context is called by-name cause it's only needed when something fails.
   */
  protected final def argumentTypesLoaded(method: Method, context: => JdiContext): Seq[Type] = {
    Try(method.argumentTypes()) match {
      case Success(types) => types
      case Failure(cnl: ClassNotLoadedException) =>
        context.loadClass(cnl.className)
        argumentTypesLoaded(method, context)
      case Failure(otherError) => throw otherError
    }
  }

  /** Gets underlying primitive from proxy or object if primitive is not needed. */
  protected def getValue(ofType: Type, fromProxy: JdiProxy): Value = (ofType, fromProxy) match {
    case (_: PrimitiveType, value: BoxedJdiProxy[_, _]) => value.primitive
    case (_, proxy) => proxy.__underlying
  }
}

/**
 * Base operation on standard methods.
 */
trait BaseMethodInvoker extends MethodInvoker {

  // name of method
  protected def methodName: String

  // reference to search (could be object or ClassType in the case of Java static members)
  protected def referenceType: ReferenceType

  // arguments of call
  protected def args: Seq[JdiProxy]

  // method match for this call
  private def matchesSignature(method: Method): Boolean =
    !method.isAbstract &&
    method.arity == args.length &&
      checkTypes(argumentTypesLoaded(method, args.head.proxyContext))

  private final def checkTypes(types: Seq[Type], arguments: Seq[JdiProxy]): Boolean =
    arguments.zip(types).forall((conformsTo _).tupled)

  protected final def checkTypes(types: Seq[Type]): Boolean =
    checkTypes(types, args)

  protected final def checkTypesRight(types: Seq[Type]): Boolean =
    checkTypes(types.reverse, args.reverse)

  private final def generateArguments(types: List[Type], arguments: Seq[JdiProxy]): Seq[Value] =
   types.zip(arguments).map((getValue _).tupled)

  /**
   * Generates arguments for given call - transform boxed primitives to unboxed ones if needed
   */
  protected final def generateArguments(method: Method): Seq[Value] =
    generateArguments(method.argumentTypes.toList, args)

  protected final def generateArgumentsRight(method: Method): Seq[Value] =
    generateArguments(method.argumentTypes.toList.reverse, args.reverse).reverse

  // search for all visible methods
  protected final def allMethods: Seq[Method] = referenceType.visibleMethods.filter(_.name == methodName)

  // found methods
  protected def matching: Seq[Method] = allMethods.filter(matchesSignature)

  // handles situation when you have multiple overloads
  protected def handleMultipleOverloads(candidates: Seq[Method], invoker: Method => Value): Option[Value] = {
    candidates match {
      case Nil => None
      case method +: Nil =>
        Some(invoker(method))
      case multiple @ head +: rest =>
        logger.warn(multipleOverloadsMessage(multiple))
        Some(invoker(head))
    }
  }

  // message for multiple overloads of method def
  private final def multipleOverloadsMessage(methods: Seq[Method]): String = {
    val overloads = methods.map(prettyPrint).mkString("\t", "\n\t", "")
    s"Multiple overloaded methods found, using first one. This may not be correct. Possible overloads:\n$overloads"
  }
}
