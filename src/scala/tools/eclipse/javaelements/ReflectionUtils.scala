/*
 * Copyright 2005-2008 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import java.lang.reflect.{ AccessibleObject, Constructor }
import java.security.{ AccessController, PrivilegedAction }

trait ReflectionUtils {
  def getMethod[T](clazz : Class[T], name : String, paramTypes : Class[_]*) =
    privileged {
      val method = clazz.getDeclaredMethod(name, paramTypes : _*)
      method.setAccessible(true)
      method
    }
  
  def privileged[T](body : => T) = {
    AccessController.doPrivileged(new PrivilegedAction[T] {
      def run() = { body }
    })
  }
}
