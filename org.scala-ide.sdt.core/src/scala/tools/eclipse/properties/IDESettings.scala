/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.properties

import scala.tools.nsc.Settings

object IDESettings {

  import ScalaPluginSettings._
  case class Box(name: String, userSettings: List[Settings#Setting])

  def shownSettings(s : Settings) : List[Box] = {
    import s._

    List(
      Box("Standard",
        List(deprecation, g, optimise, target, unchecked,
             pluginOptions, nospecialization, verbose, explaintypes, nowarn)),
      Box("Advanced",
    	List(checkInit, Xchecknull, elidebelow,
             Xexperimental, future, XlogImplicits,
             Xmigration28, noassertions, nouescape, plugin, disable,
             require, pluginsDir, Xwarnfatal)),
      Box("Private",
        List(Ynogenericsig, noimports,
             selfInAnnots, Yrecursion, refinementMethodDispatch,
             Ywarndeadcode, Ybuildmanagerdebug)),
      Box("Presentation Compiler",
        List(YpresentationDebug, YpresentationVerbose, YpresentationLog, YpresentationReplay, YpresentationDelay)))
  }
  
  def pluginSettings: List[Box] =
    List(Box("Scala Plugin Debugging", List(YPlugininfo)))
  
  def buildManagerSettings: List[Box] =
    List(Box("Build manager", List(buildManager, compileOrder, stopBuildOnErrors)))
}

object ScalaPluginSettings extends Settings {
  val YPlugininfo = BooleanSetting("-plugininfo", "Enable logging of the Scala Plugin info")
  val buildManager = ChoiceSetting("-buildmanager", "which", "Build manager to use", List("refined", "sbt"), "sbt")
  val compileOrder = ChoiceSetting("-compileorder", "which", "Compilation order",
      List("Mixed", "JavaThenScala", "ScalaThenJava"), "Mixed")
  val stopBuildOnErrors = BooleanSetting("-stopBuildOnError", "Stop build if dependent projects have errors.")
}
