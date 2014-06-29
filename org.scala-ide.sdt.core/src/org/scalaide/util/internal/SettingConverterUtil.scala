package org.scalaide.util.internal

/** Utility to unify how we convert settings to preference names */
object SettingConverterUtil {
  val USE_PROJECT_SETTINGS_PREFERENCE="scala.compiler.useProjectSettings"
  val SCALA_DESIRED_INSTALLATION="scala.compiler.installation"
  /** Warning:
   *   This should no longer be user-accessible. Do not use in UI.
   *   It's a hail Mary preference deduced from the above, to be saved
   *   and used in case aforementioned above becomes no longer resolvable.
   */
   val SCALA_DESIRED_SOURCELEVEL="scala.compiler.sourceLevel"


  /** Creates preference name from "name" of a compiler setting. */
  def convertNameToProperty(name : String) = {
    //Returns the underlying name without the -
    name.substring(1)
  }
}
