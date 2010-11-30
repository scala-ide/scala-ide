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
    		Box("Standard options",
    				List(deprecation, g, optimise, target, unchecked,
                 pluginOptions, nospecialization)),
    		Box("Advanced options",
    				List(checkInit, Xchecknull, Xdce,
    						 Xexperimental, future, XlogImplicits,
    						 Xmigration28, noassertions, nouescape, plugin, disable,
    						 require, pluginsDir, Xwarnfatal, Xwarninit)),
    		Box("Private options",
    				List(Xcloselim, inline, Xlinearizer, Ynogenericsig, noimports,
    						 nopredefs, selfInAnnots, refinementMethodDispatch,
    						 Ywarndeadcode, Ybuildmanagerdebug, Xsqueeze)))
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
