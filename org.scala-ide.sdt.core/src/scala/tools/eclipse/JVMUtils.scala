/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.reflect.NameTransformer
import scala.tools.nsc.interactive.Global

trait JVMUtils { self : Global =>

  lazy val dummyBuilder = self.ask { () => new genASM.JPlainBuilder(null) }
  
  def javaName(sym : Symbol): String = sym.javaBinaryName.toString()
  
  def javaNames(syms : List[Symbol]): Array[String] = syms.toArray map (s => javaName(s))
  
  def javaFlags(sym : Symbol) : Int = genASM.javaFlags(sym)

  def javaType(t: Type) = dummyBuilder.javaType(t)
  
}
