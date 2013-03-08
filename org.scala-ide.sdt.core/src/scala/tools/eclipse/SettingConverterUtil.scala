/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

/** Utility to unify how we convert settings to preference names */
object SettingConverterUtil {
  val USE_PROJECT_SETTINGS_PREFERENCE="scala.compiler.useProjectSettings"

  /** Creates preference name from "name" of a compiler setting. */
  def convertNameToProperty(name : String) = {
    //Returns the underlying name without the -
    name.substring(1)
  }
}
