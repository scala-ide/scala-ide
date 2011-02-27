package scala.tools.nsc
package interactive.compat

import _root_.scala.tools.nsc.{Settings => MainSettings}

class Settings(errorFn: String => Unit) extends MainSettings(errorFn) {

  def ChoiceSetting(name: String, descr: String, choices: List[String], default: String) : ChoiceSetting =
    ChoiceSetting(name, "", descr, choices, default)

}

object Info {
  val scalaVersion = "2.9.0-SNAPSHOT"
}