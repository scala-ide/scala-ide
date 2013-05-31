/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.util

import java.lang.reflect.{ AccessibleObject, Constructor }
import java.security.{ AccessController, PrivilegedAction, PrivilegedActionException }

trait ReflectionUtils {
  def getConstructor[T](clazz : Class[T], paramTypes : Class[_]*) =
    privileged {
      val ctor = clazz.getConstructor(paramTypes : _*)
      ctor.setAccessible(true)
      ctor
    }

  def getMethod[T](clazz : Class[T], name : String, paramTypes : Class[_]*) =
    privileged {
      val method = clazz.getMethod(name, paramTypes : _*)
      method.setAccessible(true)
      method
    }

  def getField[T](clazz : Class[T], name : String) =
    privileged {
      val field = clazz.getField(name)
      field.setAccessible(true)
      field
    }

  def getDeclaredConstructor[T](clazz : Class[T], paramTypes : Class[_]*) =
    privileged {
      val ctor = clazz.getDeclaredConstructor(paramTypes : _*)
      ctor.setAccessible(true)
      ctor
    }

  def getDeclaredMethod[T](clazz : Class[T], name : String, paramTypes : Class[_]*) =
    privileged {
      val method = clazz.getDeclaredMethod(name, paramTypes : _*)
      method.setAccessible(true)
      method
    }

  def getDeclaredField[T](clazz : Class[T], name : String) =
    privileged {
      val field = clazz.getDeclaredField(name)
      field.setAccessible(true)
      field
    }

  def privileged[T](body : => T) = {
    try {
      AccessController.doPrivileged(new PrivilegedAction[T] {
        def run() = { body }
      })
    }
    catch {
      case ex : PrivilegedActionException => throw ex.getCause
    }
  }
}
