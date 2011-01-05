/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse
package internal.logging

/**
 * Defensive is an helper 
 * * to "instrument" code
 * * to quick fix code (NPE)
 * * to gracefully failed (avoid exception to break the full dataflow)
 * WITHOUT forgot there is something to fix (later).
 * 
 * It's more code flow safe than exception, or assert
 * It's a complementary guardian to other tools like try { ... } catch {...}
 * 
 * Usage :
 * <br/> replace : <code>if (x != null) { ... }</code>
 * <br/> by : <code>if (Defensive.notNul(x , "x")) { ... }</code>
 */
object Defensive {
  private def log(format : String, args : Any*) {
    //TODO log in Eclipse Error Log
    System.err.println("ScalaPlugin--Defensive--" + Thread.currentThread().getName() + "--:" + format.format(args))
    Thread.dumpStack()
  }
  def notNull(o : AnyRef, format : String, args : Any*) : Boolean = {
    val back = o != null
    if (!back) {
      log("isNull " + format, args)
    }
    back
  }
  
  def notEmpty(s : String, format : String, args : Any*) : Boolean = {
    val back = (s != null && s.trim().length() > 0)
    if (!back) {
      log("isEmpty " + format, args)
    }
    back
  }
  
  def notEmpty(a : Array[Char], format : String, args : Any*) : Boolean = {
    val back = (a != null && a.length > 0);
    if (!back) {
      log("isEmpty " + format, args)
    }
    back
  }
  
  def check(assertion : Boolean , format : String, args : Any*) : Boolean = {
    if (!assertion) {
      log("assertion failed " + format, args)
    }
    assertion
  }
  
  def tryOrLog(f : => Unit) = {
    try {
      f
    } catch {
      case t => ScalaPlugin.plugin.logError(t)
    }
  }
  
  def tryOrLog[T](default : => T)(f : => T) = {
    try {
      f
    } catch {
      case t => {
        ScalaPlugin.plugin.logError(t)
        default
      }
    }
  }
  
     

}
