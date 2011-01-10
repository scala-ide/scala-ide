/*
 * Copyright 2005-2010 LAMP/EPFL
 * @author Josh Suereth
 */
// $Id$

package scala.tools.eclipse.properties

import org.eclipse.core.runtime.preferences.InstanceScope
import org.eclipse.core.runtime.preferences.{ AbstractPreferenceInitializer, DefaultScope }

import scala.tools.nsc.Settings

import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.SettingConverterUtil._
import scala.tools.eclipse.util.IDESettings

/**
 * This is responsible for initializing Scala Compiler
 * Preferences to their default values.
 */
class ScalaCompilerPreferenceInitializer extends AbstractPreferenceInitializer {
  
  /** Actually initializes preferences */
  def initializeDefaultPreferences() : Unit = {
	  
    ScalaPlugin.plugin.check {
      
      val settings = new Settings
      val defaultPrefs = new DefaultScope().getNode(ScalaPlugin.plugin.pluginId)
      val workspacePrefs = new InstanceScope().getNode(ScalaPlugin.plugin.pluginId)
      
      def defaultPreference(s: Settings#Setting) {
      	val preferenceName = convertNameToProperty(s.name)
          s match {
            case bs : SettingsAddOn#BooleanSettingD => {
              defaultPrefs.put(preferenceName, bs.default.toString)
              bs.value = workspacePrefs.getBoolean(preferenceName, bs.default)
            }
            case bs : Settings#BooleanSetting => {
              defaultPrefs.put(preferenceName, "false")
              bs.value = workspacePrefs.getBoolean(preferenceName, false)
            }
            case is : Settings#IntSetting => {
              defaultPrefs.put(preferenceName, is.default.toString)
              is.value = workspacePrefs.getInt(preferenceName, is.default)
            }
            case ss : Settings#StringSetting => {
              defaultPrefs.put(preferenceName, ss.default)
              ss.value = workspacePrefs.get(preferenceName, ss.default)
            }
            case ms : Settings#MultiStringSetting => {
              defaultPrefs.put(preferenceName, "") 
              ms.value = workspacePrefs.get(preferenceName, "").trim.split(" +").toList
            }
            case cs : Settings#ChoiceSetting => {
              defaultPrefs.put(preferenceName, cs.default)
              cs.value = workspacePrefs.get(preferenceName, cs.default)
            }
          }
      }

      IDESettings.shownSettings(settings).foreach {_.userSettings.foreach (defaultPreference)}
      IDESettings.pluginSettings.foreach {_.userSettings.foreach (defaultPreference)}
      IDESettings.tuningSettings.foreach {_.userSettings.foreach (defaultPreference)}
    }
  }
}
