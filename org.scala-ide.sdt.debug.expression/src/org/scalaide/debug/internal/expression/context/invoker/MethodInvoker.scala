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
import org.scalaide.debug.internal.expression.proxies.JdiProxyWrapper
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
    else (tpe, proxy, proxy.__underlying.referenceType) match {

      case (arrayType: ArrayType, arrayProxy: ArrayJdiProxy[_], _) => true

      case (_, wrapper: JdiProxyWrapper, _) => conformsTo(wrapper.__outer, tpe)

      case _ if tpe == proxy.referenceType => true

      case (primitive: PrimitiveType, boxed: BoxedJdiProxy[_, _], _) => primitive.name == boxed.primitiveName

      case (objectType, _, refType: ReferenceType) if objectType.name == "java.lang.Object" => true

      case (refType: ClassType, _, classType: ClassType) => isSuperClassOf(refType)(classType)

      case (interfaceType: InterfaceType, _, classType: ClassType) => classType.allInterfaces().contains(interfaceType)

      case _ => false
    }
  } onSide { result =>
    val verb = if (result) " matches " else " does not match "
    val proxyTpe = Option(proxy.__underlying).map(_.referenceType.toString).getOrElse("null")
    logger.debug("Proxy of type: " + proxyTpe + verb + tpe)
  }

  private def isSuperClassOf(parentClass: ClassType)(thisClass: ClassType): Boolean = {
    parentClass == thisClass || Option(thisClass.superclass()).map(isSuperClassOf(parentClass)).getOrElse(false)
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
}

/**
 * Base operation on standard methods.
 */
abstract class BaseMethodInvoker extends MethodInvoker {

  // name of method
  def methodName: String

  // reference to search (could be object or ClassType in the case of Java static members)
  def referenceType: ReferenceType

  // arguments of call
  val args: Seq[JdiProxy]

  // method match for this call
  private def methodMatch(method: Method) =
    method.argumentTypeNames().size() == args.length &&
      checkTypes(argumentTypesLoaded(method, args.head.proxyContext))

  protected final def checkTypes(types: Seq[Type]): Boolean = types.zip(args).forall {
    case (tpe, proxy) => conformsTo(proxy, tpe)
  }

  /**
   * Generates arguments for given call - transform boxed primitives to unboxed ones if needed
   */
  protected final def generateArguments(method: Method): Seq[Value] = {
    def mapSingleArgument(args: (Type, JdiProxy)): Value = args match {
      case (tpe, parent: JdiProxyWrapper) => mapSingleArgument(tpe -> parent.__outer)
      case (_: PrimitiveType, value: BoxedJdiProxy[_, _]) => value.primitive
      case (_, proxy) => proxy.__underlying
    }
    method.argumentTypes().zip(args).map(mapSingleArgument)
  }

  // search for all visible methods
  protected final def allMethods: Seq[Method] = referenceType.visibleMethods.filter(_.name == methodName)

  // found methods
  protected final def matching: Seq[Method] = allMethods.filter(methodMatch)

  // message for multiple overloads of method def
  protected final def multipleOverloadsMessage(methods: Seq[Method]): String = {
    val overloads = methods.map(prettyPrint).mkString("\t", "\n\t", "")
    s"Multiple overloaded methods found, using first one. This may not be correct. Possible overloads:\n$overloads"
  }
}