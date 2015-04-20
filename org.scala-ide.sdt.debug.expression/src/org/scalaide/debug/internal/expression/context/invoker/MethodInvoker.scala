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
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.expression.proxies.ObjectJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.PrimitiveJdiProxy
import org.scalaide.logging.HasLogger

import com.sun.jdi._

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
    def isBoxedPrimitive(name: String) = Names.Java.boxed.all.contains(name)
    if (proxy.__value == null) tpe.isInstanceOf[ClassType]
    else (tpe, proxy, proxy.__type) match {
      // check for boxed types
      case (classType: ClassType, primitiveProxy: PrimitiveJdiProxy[_, _, _], _) if isBoxedPrimitive(classType.name) =>
        classType.name == primitiveProxy.boxedName
      case (primitive: PrimitiveType, primitiveProxy: PrimitiveJdiProxy[_, _, _], _) =>
        primitive == primitiveProxy.__type
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

  /** Boxes proxy value if type is a `ReferenceType`. */
  protected def autobox(tpe: Type, proxy: JdiProxy): Value = (proxy, tpe) match {
    case (primitive: PrimitiveJdiProxy[_, _, _], objectType: ReferenceType) => primitive.boxed
    case (other, _) => other.__value
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

  // basic arguments of call
  protected def args: Seq[JdiProxy]

  // augmented arguments of call, includes additional parameter for static methods from super traits (if needed)
  protected def methodArgs(method: Method): Seq[JdiProxy] = args

  // method match for this call
  private def matchesSignature(method: Method): Boolean =
    !method.isAbstract &&
    method.arity == methodArgs(method).length &&
    checkTypes(argumentTypesLoaded(method, methodArgs(method).head.__context), method)

  private final def checkTypes(types: Seq[Type], arguments: Seq[JdiProxy]): Boolean =
    arguments.zip(types).forall((conformsTo _).tupled)

  protected final def checkTypes(types: Seq[Type], method: Method): Boolean =
    checkTypes(types, methodArgs(method))

  protected final def checkTypesRight(types: Seq[Type], method: Method): Boolean =
    checkTypes(types.reverse, methodArgs(method).reverse)

  private final def generateArguments(types: Seq[Type], arguments: Seq[JdiProxy]): Seq[Value] =
    types.zip(arguments).map { case (tpe, arg) => autobox(tpe, arg) }

  /**
   * Generates arguments for given call - transform boxed primitives to unboxed ones if needed
   */
  protected final def generateArguments(method: Method): Seq[Value] =
    generateArguments(method.argumentTypes, methodArgs(method))

  protected final def generateArgumentsRight(method: Method): Seq[Value] =
    generateArguments(method.argumentTypes.reverse, methodArgs(method).reverse).reverse

  // search for all visible methods
  protected def allMethods: Seq[Method] =
    // both visibleMethods and allMethods calls are required to maintain proper order of methods
    (referenceType.visibleMethods ++ referenceType.allMethods).filter(_.name == methodName)

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
