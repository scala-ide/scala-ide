/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.util

import scala.tools.eclipse.properties.SettingsAddOn
import scala.tools.nsc.Settings


object IDESettings {
  import ScalaPluginSettings._
  
	case class Box(name: String, userSettings: List[Settings#Setting])
	
  def shownSettings(s : Settings) : List[Box] = {
    import s._

    List(
    		Box("Standard options",
    				List(deprecation, g, optimise, target, unchecked,
                 pluginOptions, nospecialization)),
    		Box("Advanced options",
    				List(checkInit, Xchecknull, elidebelow,
    						 Xexperimental, future, XlogImplicits,
    						 Xmigration28, noassertions, nouescape, plugin, disable,
    						 require, pluginsDir, Xwarnfatal, Xwarninit)),
    		Box("Private options",
    				List(Xcloselim, Xdce, inline, Xlinearizer, Ynogenericsig, noimports,
    						 selfInAnnots, Yrecursion, refinementMethodDispatch,
    						 Ywarndeadcode, Ybuildmanagerdebug))
        // BACK-2.8    						 
        //Box("Presentation Compiler",
        //    List(YpresentationDebug, YpresentationVerbose))
    )
  }
  
  def pluginSettings: List[Box] = {
    val Yplugininfo = BooleanSetting("-plugininfo", "Print Scala plugin debugging info")
	  List(Box("Scala Plugin Debugging", List(Yplugininfo)))    
  }
  
  def tuningSettings: List[Box] = {
    List(
        Box("Editor Tuning", List(compileOnTyping))
        , Box("Logging Tuning", List(tracerEnabled))
    )    
  }
  
  val compileOnTyping = BooleanSetting("_auto compile", "compile file on typing (else compile on save)", true)
  
  // TODO remove compileOnTypingDelay (useless)
  val compileOnTypingDelay = IntSetting("_auto compile delay", "compile file on typing, delay (ms), 0 : immediate", 600, Some((0,3000)), parseInt)
  
  val tracerEnabled = BooleanSetting("_tracer printing", "print tracer info on stdout/stderr", false)

  def parseInt(s : String) : Option[Int] = {
    try {
      Some( s.toInt )
    } catch {
      case _ => None
    }
  }
}

object ScalaPluginSettings extends SettingsAddOn {
	val YPlugininfo = BooleanSetting("-plugininfo", "Enable logging of the Scala Plugin info")
}

