package scala.tools.nsc
package interactive.compat

import _root_.scala.tools.nsc.{Settings => MainSettings}


class Settings(errorFn: String => Unit) extends MainSettings(errorFn) {
  // BACK from 2.9.0-SNAPSHOT
  /**
   * helpArg is ignored (only available since 2.9)
   */
  def ChoiceSetting(name: String,  helpArg: String,descr: String, choices: List[String], default: String) : ChoiceSetting =
    ChoiceSetting(name, descr, choices, default)

  /**
   * IDE-specific settings
   */
  val YpresentationVerbose = BooleanSetting("-Ypresentation-verbose", "Print information about presentation compiler tasks.")
  val YpresentationDebug   = BooleanSetting("-Ypresentation-debug",  "Enable debugging output for the presentation compiler.")
  
  val YpresentationLog     = StringSetting("-Ypresentation-log", "file", "Log presentation compiler events into file", "")
  val YpresentationReplay  = StringSetting("-Ypresentation-replay", "file", "Replay presentation compiler events from file", "")
  val YpresentationDelay   = IntSetting("-Ypresentation-delay", "Wait number of ms after typing before starting typechecking", 0, Some(0, 999), str => Some(str.toInt))

}

object Info {
  val scalaVersion = "2.8.1"
}