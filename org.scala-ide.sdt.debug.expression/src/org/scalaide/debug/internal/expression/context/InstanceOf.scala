/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context

import scala.annotation.tailrec
import scala.collection.JavaConversions._

import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.proxies.JdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.BooleanJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.BoxedJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.NullJdiProxy
import org.scalaide.debug.internal.expression.proxies.primitives.UnitJdiProxy

import com.sun.jdi.ArrayType
import com.sun.jdi.ClassType
import com.sun.jdi.InterfaceType
import com.sun.jdi.ReferenceType

import TypeNames._

/**
 * Implements `isInstanceOfCheck` method used to mock `isInstanceOf`.
 */
private[context] trait InstanceOf {
  self: Proxyfier =>

  /**
   * Checks if value under proxy conforms to given type.
   *
   * WARNING - this method is used in reflective compilation.
   * If you change its name, package or behavior, make sure to change it also.
   *
   * @param proxy proxy to check
   * @param typeName name of type to check against
   * @param BooleanJdiProxy
   */
  final def isInstanceOfCheck(proxy: JdiProxy, typeName: String): BooleanJdiProxy =
    valueProxy(this.mirrorOf(isInstanceOf(proxy, fixScalaObjectType(typeName)))).asInstanceOf[BooleanJdiProxy]

  private def fixScalaObjectType(name: String) = {
    if(name.endsWith(".type")) name.dropRight(".type".length) + "$"
    else name
  }

  /**
   * Checks if proxy matches given type.
   * Handles null, Unit, primitives and delegates everything else to `handleObject`.
   */
  private def isInstanceOf(proxy: JdiProxy, typeName: String): Boolean = proxy match {
    case nullProxy: NullJdiProxy =>
      false
    case unitProxy: UnitJdiProxy =>
      typeName == fixScalaPrimitives(Scala.unitType)
    case _ if proxy.referenceType.name == Scala.boxedUnitType =>
      typeName == fixScalaPrimitives(Scala.unitType)
    case boxedProxy: BoxedJdiProxy[_, _] =>
      val scalaPrimitiveName = fixScalaPrimitives(javaNameToScalaName(boxedProxy.primitive.`type`.name))
      scalaPrimitiveName == typeName
    case other => handleObject(other, typeName)
  }

  /**
   * Checks if proxy matches given type.
   * Handles Classes, Interfaces and Arrays (no variance support for now).
   */
  private def handleObject(proxy: JdiProxy, typeName: String): Boolean = proxy.referenceType match {
    case array: ArrayType =>
      val scalaComponentType = fixScalaPrimitives(javaNameToScalaName(array.componentTypeName))
      // TODO add support for variance - this needs some integration with `MethodInvoker.conformsTo`
      typeName == Scala.Array(scalaComponentType)
    case interface: InterfaceType =>
      val parents: Set[String] = (interface +: interface.subinterfaces)
        .map(_.name)(collection.breakOut)
      parents.contains(typeName)
    case clazz: ClassType =>
      val parents: Set[String] = ((clazz +: clazz.allInterfaces) ++ superclasses(clazz))
        .map(_.name)(collection.breakOut)
      parents.contains(typeName)
  }

  private def superclasses(clazz: ClassType): Seq[ClassType] = {
    @tailrec def loop(clazz: ClassType, result: Seq[ClassType]): Seq[ClassType] = {
      val superclass = clazz.superclass
      if (superclass == null) result
      else loop(superclass, result :+ superclass)
    }
    loop(clazz, Seq.empty)
  }
}
