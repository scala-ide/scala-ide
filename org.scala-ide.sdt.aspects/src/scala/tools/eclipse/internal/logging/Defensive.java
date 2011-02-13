/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.internal.logging;

/**
 * Defensive is an helper to "instrument" code or quick fix code (NPE) without
 * forgotting there is something to fix (later).
 * It's more code flow safe than exception, or assert
 * It's a complementary guardian to other tools like try { ... } catch {...}
 * Usage :
 * <br/> replace : <code>if (x != null) { ... }</code>
 * <br/> by : <code>if (Defensive.notNul(x , "x")) { ... }</code>
 */
public class Defensive {
  private static void log(String format, Object... args) {
    //TODO log in Eclipse Error Log
    System.err.println("ScalaPlugin--Defensive--" + Thread.currentThread().getName() + "--:" + String.format(format, args));
    Thread.dumpStack();
  }
  public static boolean notNull(Object o, String format, Object... args) {
    boolean back = o != null;
    if (!back) {
      log("isNull " + format, args);
    }
    return back;
  }
  
  public static boolean notEmpty(String s , String format, Object... args) {
    boolean back = (s != null && s.trim().length() > 0);
    if (!back) {
      log("isEmpty " + format, args);
    }
    return back;
  }
  
  public static boolean notEmpty(char[] a , String format, Object... args) {
    boolean back = (a != null && a.length > 0);
    if (!back) {
      log("isEmpty " + format, args);
    }
    return back;
  }
  
  public static <T> boolean check(boolean assertion , String format, Object... args) {
    if (!assertion) {
      log("assertion failed " + format, args);
    }
    return assertion;
  }
}
