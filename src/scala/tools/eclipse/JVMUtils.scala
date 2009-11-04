/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.tools.nsc.Global 
import ch.epfl.lamp.fjbg.JType

trait JVMUtils { self : Global =>

  private lazy val codeGenerator =
    currentRun.phaseNamed(genJVM.phaseName).asInstanceOf[genJVM.JvmPhase].codeGenerator

  def javaName(sym : Symbol) : String = codeGenerator.javaName(sym)
  
  def javaNames(syms : List[Symbol]) : Array[String] = codeGenerator.javaNames(syms)
  
  def javaFlags(sym : Symbol) : Int = codeGenerator.javaFlags(sym)
  
  def javaType(t : Type) : JType = codeGenerator.javaType(t)
  
  def javaType(sym : Symbol) : JType = codeGenerator.javaType(sym)
}
