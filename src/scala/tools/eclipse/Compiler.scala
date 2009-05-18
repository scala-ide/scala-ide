/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse

import scala.tools.nsc.Global
import scala.tools.nsc.io.{ AbstractFile, PlainFile }

// TODO Switch to IFile, probably eliminate this in favour of IProject

trait CompilerProject {
  def charSet(file : PlainFile) : String
  def initialize(compiler : Global) : Unit

  def projectFor(path : String) : Option[CompilerProject]
  def fileFor(path : String) : PlainFile // relative to workspace
  def signature(file : PlainFile) : Long
  def setSignature(file : PlainFile, value : Long) : Unit
  def refreshOutput : Unit
  def resetDependencies(file : PlainFile) : Unit
  def dependsOn(file : PlainFile, what : PlainFile) : Unit
  
  def buildError(file : PlainFile, severity0 : Int, msg : String, offset : Int, identifier : Int) : Unit
  def buildError(severity0 : Int, msg : String) : Unit
  def clearBuildErrors(file : AbstractFile) : Unit  
  def clearBuildErrors() : Unit  
  def hasBuildErrors(file : PlainFile) : Boolean
}
