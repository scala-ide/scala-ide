package scala.tools.eclipse.properties

import scala.tools.nsc.interactive.compat.Settings


trait SettingsAddOn extends Settings {
    
  def BooleanSetting(name: String, descr: String, default : Boolean) = {
    val b = new BooleanSettingD(name, descr, default)
    allSettings += b
    b
  }
  
  /**
   *  A setting represented by a boolean with support for default value
   *  (not provide by default BooleanSetting, can't inherit BooleanSetting
   *  because BooleanSetting use private Constructor)
   */
  class BooleanSettingD(
    name: String,
    descr: String,
    val default : Boolean
    )
  extends Setting(name, descr) {
    type T = Boolean
    protected var v = default
  
    def tryToSet(args: List[String]) = { value = true ; Some(args) }
    def unparse: List[String] = if (value) List(name) else Nil
    override def tryToSetFromPropertyValue(s : String) {
      value = s.equalsIgnoreCase("true")
    }
  }
}
