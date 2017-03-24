/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package context

import scala.collection.JavaConverters._
import scala.reflect.NameTransformer

import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala

import com.sun.jdi.ArrayType
import com.sun.jdi.ClassType
import com.sun.jdi.InterfaceType
import com.sun.jdi.InvocationException
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType
import com.sun.jdi.StackFrame
import com.sun.jdi.Value
import com.sun.jdi.VirtualMachine

/**
 * Part of JdiContext responsible for searching classes, objects and methods by name.
 */
trait Seeker {
  self: JdiClassLoader =>

  protected def jvm: VirtualMachine

  private def handleArray(name: String): String = name match {
    case Scala.Array(typeArg) => Java.primitives.Array(handleArray(scalaToJavaTypeName(typeArg)))
    case other => other
  }

  /** converts some Scala names to java ones */
  private def scalaToJavaTypeName(scalaName: String): String = scalaName match {
    case "String" => Java.String
    case "Boolean" => Java.primitives.boolean
    case "Byte" => Java.primitives.byte
    case "Char" => Java.primitives.char
    case "Double" => Java.primitives.double
    case "Float" => Java.primitives.float
    case "Int" => Java.primitives.int
    case "Long" => Java.primitives.long
    case "Short" => Java.primitives.short
    case other => other
  }

  /** Looks up an array type for given name and returns jdi reference to it. */
  final def arrayByName(name: String): ArrayType =
    tryByName[ArrayType](name)
      .getOrElse(throw new IllegalArgumentException(s"Returned type is not an array: $name"))

  /** Looks up a class for given name and returns jdi reference to it. */
  final def classByName(name: String): ClassType =
    tryByName[ClassType](name)
      .getOrElse(throw new ClassNotFoundException("Class or object not found: " + handleArray(name)))

  final def tryByName[Type <: com.sun.jdi.ReferenceType: scala.reflect.ClassTag](name: String): Option[Type] = {
    val className = handleArray(name)
    def getType() = jvm.classesByName(className).asScala.collectFirst {
      case tpe: Type => tpe
    }

    getType().orElse {
      loadClass(className)
      getType()
    }
  }

  /** Looks up an interface for given name and returns jdi reference to it. */
  final def interfaceByName(interfaceName: String, onNotFound: String => InterfaceType): InterfaceType = {
    jvm.classesByName(interfaceName).asScala.collectFirst {
      case interfaceType: InterfaceType => interfaceType
    } getOrElse onNotFound(interfaceName)
  }

  /** Looks up for a Scala object with given name and returns jdi reference to it. */
  final def objectByName(name: String): ObjectReference = {
    val classType = tryByName[ClassType](name + "$")
      .getOrElse(throw new ClassNotFoundException("Class not found: " + handleArray(name) + "$"))
    val field = Option(classType.fieldByName(NameTransformer.MODULE_INSTANCE_NAME))
      .getOrElse(throw new NoSuchMethodError(s"No field named `${NameTransformer.MODULE_INSTANCE_NAME}` found in class $name"))
    classType.getValue(field).asInstanceOf[ObjectReference]
  }

  private[expression] final def tryObjectByName(name: String): Option[ObjectReference] = try {
    for {
      classType <- tryByName[ClassType](name + "$")
      field <- Option(classType.fieldByName(NameTransformer.MODULE_INSTANCE_NAME))
    } yield classType.getValue(field).asInstanceOf[ObjectReference]
  } catch {
    // thrown when trying to force loading nonexistent class
    case _: InvocationException => None
  }

  /**
   * Helper for getting methods from given class name.
   * `methodName` is encoded if needed (like `+:` is changed to `$plus$colon`).
   *
   * @param className name of class to load method from
   * @param methodName name of method
   * @param arity arity of method
   * @throws ClassNotFoundException when class does not exist
   * @throws NoSuchMethodError when method is not found
   */
  final def methodOn(className: String, methodName: String, arity: Int): Method = {
    val classRef = jvm.classesByName(className).asScala.headOption.getOrElse(
      throw new ClassNotFoundException(s"Class with name $className not found."))
    methodOn(classRef, methodName, arity)
  }

  /**
   * Helper for getting methods from given class reference.
   * `methodName` is encoded if needed (like `+:` is changed to `$plus$colon`).
   *
   * @param tpe type to load method from
   * @param methodName name of method
   * @param arity arity of method
   * @throws NoSuchMethodError when method is not found
   */
  final def methodOn(tpe: ReferenceType, methodName: String, arity: Int): Method = {
    tpe.methodsByName(NameTransformer.encode(methodName)).asScala.find(_.arity == arity).getOrElse(
      throw new NoSuchMethodError(s"Method: $methodName with arity: $arity not found on ${tpe.name}"))
  }

  /**
   * Helper for getting method from ObjectReference
   *
   * @param obj reference on which method is looked
   * @param methodName name of method
   * @param arity arity of method
   * @throws NoSuchMethodError when method is not found
   */
  final def methodOn(obj: ObjectReference, methodName: String, arity: Int): Method = {
    val methods = obj.referenceType().methodsByName(NameTransformer.encode(methodName))
    methods.asScala.find(_.arity == arity).getOrElse(
      throw new NoSuchMethodError(s"Method: $methodName with arity: $arity not found on ${obj.`type`}"))
  }

  /**
   * Extracts value from debug frame.
   * If one does not exists, returns None.
   */
  protected def valueFromFrame(frame: StackFrame, name: String): Option[Value] = {
    val fieldsFromFrame = {
      val visibleVariable = Option(frame.visibleVariableByName(name))
      val visibleArgumentInNestedMethod = Option(frame.visibleVariableByName(name + "$1"))
      visibleVariable.map(frame.getValue) orElse visibleArgumentInNestedMethod.map(frame.getValue)
    }

    def fieldsFromThisScope = for {
      obj <- JdiHelpers.thisObject(frame)
      field <- Option(obj.referenceType.fieldByName(name))
    } yield obj.getValue(field)

    fieldsFromFrame orElse fieldsFromThisScope
  }
}
