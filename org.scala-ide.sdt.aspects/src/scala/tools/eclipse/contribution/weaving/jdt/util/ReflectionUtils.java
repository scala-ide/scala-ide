/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

public class ReflectionUtils {
  public static <T> Constructor<T> getConstructor(final Class<T> clazz, final Class<?> ... paramTypes) {
    try {
      return AccessController.doPrivileged(new PrivilegedExceptionAction<Constructor<T>>() {
        public Constructor<T> run() throws Exception {
          Constructor<T> ctor = clazz.getDeclaredConstructor(paramTypes);
          ctor.setAccessible(true);
          return ctor;
        }
      });
    }
    catch(PrivilegedActionException ex) {
      throw new RuntimeException(ex);
    }
  }

  public static <T> Method getMethod(final Class<T> clazz, final String name, final Class<?> ... paramTypes) {
    try {
      return AccessController.doPrivileged(new PrivilegedExceptionAction<Method>() {
        public Method run() throws Exception {
          Method m = clazz.getDeclaredMethod(name, paramTypes);
          m.setAccessible(true);
          return m;
        }
      });
    }
    catch(PrivilegedActionException ex) {
      throw new RuntimeException(ex);
    }
  }

  public static <T> Field getField(final Class<T> clazz, final String name) {
    try {
      return AccessController.doPrivileged(new PrivilegedExceptionAction<Field>() {
        public Field run() throws Exception {
          Field f = clazz.getDeclaredField(name);
          f.setAccessible(true);
          return f;
        }
      });
    }
    catch(PrivilegedActionException ex) {
      throw new RuntimeException(ex);
    }
  }
}
