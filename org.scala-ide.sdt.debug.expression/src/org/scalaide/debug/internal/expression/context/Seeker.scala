/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.context

import scala.collection.JavaConversions.asScalaBuffer

import org.scalaide.debug.internal.expression.Names.Debugger
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala

import com.sun.jdi.ArrayType
import com.sun.jdi.ClassType
import com.sun.jdi.InterfaceType
import com.sun.jdi.InvocationException
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.StackFrame
import com.sun.jdi.Value
import com.sun.jdi.VirtualMachine

/**
 * Part of JdiContext responsible for searching classes, objects and methods by name.
 */
trait Seeker {
  self: JdiClassLoader =>

  protected def jvm: VirtualMachine

  /** real name of class - replace object name prefix and handles arrays */
  private def realClassName(name: String): String = name match {
    case Debugger.PrefixedObjectOrStaticCall(realName) => realName
    case Scala.Array(typeArg) => Java.primitives.Array(realClassName(scalaToJavaTypeName(typeArg)))
    case other => other
  }

  /** converts some Scala names to java ones */
  private def scalaToJavaTypeName(scalaName: String): String = scalaName match {
    case "String" => Java.boxed.String
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

  final def arrayClassByName(name: String): ArrayType =
    jvm.classesByName(realClassName(name)).head match {
      case arrayType: ArrayType => arrayType
      case other => throw new IllegalArgumentException(s"Returned type is not an array: $other")
    }

  /** Looks up a class for given name and returns jdi reference to it. */
  final def classByName(name: String): ClassType =
    tryClassByName(name)
      .getOrElse(throw new ClassNotFoundException("Class or object not found: " + realClassName(name)))

  final def tryClassByName(name: String): Option[ClassType] = {
    val className = realClassName(name)
    def getClassType() = jvm.classesByName(className).collectFirst {
      case classType: ClassType => classType
    }

    getClassType().orElse {
      loadClass(className)
      getClassType()
    }
  }

  /** Looks up an interface for given name and returns jdi reference to it. */
  final def interfaceByName(name: String, onNotFound: String => InterfaceType): InterfaceType = {
    val interfaceName = realClassName(name)
    jvm.classesByName(interfaceName).collectFirst {
      case interfaceType: InterfaceType => interfaceType
    } getOrElse onNotFound(interfaceName)
  }

  /** Looks up for a Scala object with given name and returns jdi reference to it. */
  final def objectByName(name: String): ObjectReference = {
    val classType = tryClassByName(name + "$")
      .getOrElse(throw new ClassNotFoundException("Class not found: " + realClassName(name) + "$"))
    val field = Option(classType.fieldByName("MODULE$"))
      .getOrElse(throw new NoSuchMethodError(s"No field named `MODULE$$` found in class $name"))
    classType.getValue(field).asInstanceOf[ObjectReference]
  }

  private[expression] final def tryObjectByName(name: String): Option[ObjectReference] =
    try {
      for {
        classType <- tryClassByName(name + "$")
        field <- Option(classType.fieldByName("MODULE$"))
      } yield classType.getValue(field).asInstanceOf[ObjectReference]
    } catch {
      // thrown when trying to force loading nonexistent class
      case _: InvocationException => None
    }

  /** Helper for getting methods from given class name */
  final def methodOn(className: String, methodName: String): Method = {
    val classRef = jvm.classesByName(className).head
    classRef.methodsByName(methodName).head
  }

  /** Helper for getting method (static) from ObjectReference */
  final def methodOn(obj: ObjectReference, methodName: String): Method =
    obj.referenceType().methodsByName(methodName).head

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
      thisObject <- Option(frame.thisObject)
      referenceType <- Option(thisObject.referenceType)
      field <- Option(referenceType.fieldByName(name))
    } yield thisObject.getValue(field)

    fieldsFromFrame orElse fieldsFromThisScope
  }
}