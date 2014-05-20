/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.context

import scala.collection.JavaConversions._

import org.scalaide.debug.internal.expression.DebuggerSpecific

import com.sun.jdi.ClassType
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.StackFrame
import com.sun.jdi.Value
import com.sun.jdi.VirtualMachine

/**
 * Part of JdiContext responsible for looking classess, objects and methods by name.
 */
trait Seeker {
  self: JdiClassLoader =>

  protected def jvm: VirtualMachine


  /** real name of class - replace all object name prefix */
  private def realClassName(name: String) = if (name.startsWith(DebuggerSpecific.objNamePrefix)) name.drop(3) else name

  /** Looks up a class for given name and returns jdi reference to it. */
  final def classByName(name: String): ClassType = {
    tryClassByName(name).getOrElse(throw new RuntimeException("Class or object not found: " + realClassName(name)))
  }

  private def tryClassByName(name: String): Option[ClassType] = {
    def getClassType() = jvm.classesByName(realClassName(name)).headOption.map(_.asInstanceOf[ClassType])

    getClassType().orElse {
      loadClass(realClassName(name))
      getClassType()
    }
  }

  /** Looks up for a Scala object with given name and returns jdi reference to it. */
  final def objectByName(name: String): ObjectReference =
    tryObjectByName(name).getOrElse(throw new RuntimeException("Class or object not found: " + realClassName(name)))

  private[expression] final def tryObjectByName(name: String): Option[ObjectReference] =
    for {
      clazz <- tryClassByName(name + "$")
      field <- Option(clazz.fieldByName("MODULE$"))
    } yield clazz.getValue(field).asInstanceOf[ObjectReference]

  /** Helper for getting methods from given class name */
  protected final def methodOn(className: String, methodName: String): Method = {
    val classRef = jvm.classesByName(className).head
    classRef.methodsByName(methodName).head
  }

  /** Helper for getting method (static) from ObjectReference */
  protected final def methodOn(obj: ObjectReference, methodName: String): Method =
    obj.referenceType().methodsByName(methodName).head

  /**
   * Extracts value from debug frame.
   * If one does not exists, returns None.
   */
  protected def valueFromFrame(frame: StackFrame, name: String): Option[Value] = {
    val fieldsFromFrame = {
      val visibleVariable = Option(frame.visibleVariableByName(name))
      val visibleArgumentInNestedMethod = Option(frame.visibleVariableByName(name + "$1"))
      for {
        variable <- visibleVariable orElse visibleArgumentInNestedMethod
      } yield frame.getValue(variable)
    }

    val fieldsFromThisScope = for {
      thisObject <- Option(frame.thisObject)
      referenceType <- Option(thisObject.referenceType)
      field <- Option(referenceType.fieldByName(name))
    } yield thisObject.getValue(field)

    fieldsFromFrame orElse fieldsFromThisScope
  }

}