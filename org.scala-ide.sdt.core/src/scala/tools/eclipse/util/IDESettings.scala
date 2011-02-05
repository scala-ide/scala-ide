/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.util

import scala.tools.nsc.Settings

object IDESettings {
  case class Box(name: String, userSettings: List[Settings#Setting])
  
  def shownSettings(s : Settings) : List[Box] = {
    import s._

    List(
      Box("Standard",
        List(deprecation, g, optimise, target, unchecked,
             pluginOptions, nospecialization, verbose)),
      Box("Advanced",
    	List(checkInit, Xchecknull, elidebelow,
             Xexperimental, future, XlogImplicits,
             Xmigration28, noassertions, nouescape, plugin, disable,
             require, pluginsDir, Xwarnfatal, Xwarninit)),
      Box("Private",
        List(Xcloselim, Xdce, inline, Xlinearizer, Ynogenericsig, noimports,
             selfInAnnots, Yrecursion, refinementMethodDispatch,
             Ywarndeadcode, Ybuildmanagerdebug)),
      Box("Presentation Compiler",
        List(YpresentationDebug, YpresentationVerbose, YpresentationLog, YpresentationReplay)))
  }
  
  def pluginSettings: List[Box] = {
   import ScalaPluginSettings._
    val Yplugininfo = BooleanSetting("-plugininfo", "Print Scala plugin debugging info")
    List(Box("Scala Plugin Debugging", List(Yplugininfo)))    
  }
}

object ScalaPluginSettings extends Settings {
  val YPlugininfo = BooleanSetting("-plugininfo", "Enable logging of the Scala Plugin info")
}
