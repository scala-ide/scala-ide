/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.tools.nsc.Settings
import scala.tools.nsc.interactive.Global
import scala.tools.nsc.reporters.ConsoleReporter

import scala.tools.eclipse.javaelements.{ ScalaIndexBuilder, ScalaJavaMapper, ScalaStructureBuilder }

class ScalaPresentationCompiler(settings : Settings)
  extends Global(settings, new ConsoleReporter(settings))
  with ScalaStructureBuilder with ScalaIndexBuilder with ScalaJavaMapper {
  override def logError(msg : String, t : Throwable) =
    ScalaPlugin.plugin.logError(msg, t)
}
