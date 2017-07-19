package org.scalaide.core.internal.formatter

import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.ProjectScope

import org.scalaide.core.IScalaPlugin
import org.scalaide.core.SdtConstants
import org.scalaide.ui.internal.preferences.PropertyStore
import scalariform.formatter.preferences._

object FormatterPreferences {

  implicit class RichFormatterPreference(preference: PreferenceDescriptor[_]) {

    def eclipseKey = PREFIX + preference.key
  }

  implicit class RichPreferenceStore(preferenceStore: IPreferenceStore) {

    def apply(pref: PreferenceDescriptor[Boolean]) = preferenceStore.getBoolean(pref.eclipseKey)

    def apply(pref: PreferenceDescriptor[Int]) = preferenceStore.getInt(pref.eclipseKey)

    def apply(pref: PreferenceDescriptor[Intent]) = preferenceStore.getString(pref.eclipseKey)

    def update(pref: PreferenceDescriptor[Boolean], value: Boolean): Unit = {
      preferenceStore.setValue(pref.eclipseKey, value)
    }

    def update(pref: PreferenceDescriptor[Int], value: Int): Unit = {
      preferenceStore.setValue(pref.eclipseKey, value)
    }

    def update(pref: PreferenceDescriptor[Intent], value: Intent): Unit = {
      preferenceStore.setValue(pref.eclipseKey, value.toString)
    }

    def importPreferences(preferences: IFormattingPreferences): Unit = {
      for (preference <- AllPreferences.preferences)
        preference match {
          case pd: BooleanPreferenceDescriptor => preferenceStore.setValue(pd.eclipseKey, preferences(pd))
          case pd: IntegerPreferenceDescriptor => preferenceStore.setValue(pd.eclipseKey, preferences(pd))
          case pd: IntentPreferenceDescriptor =>
            preferenceStore.setValue(
              pd.eclipseKey, preferences(pd).toString
            )
        }
    }

  }

  def getPreferences: IFormattingPreferences = getPreferences(IScalaPlugin().getPreferenceStore)

  def getPreferences(preferenceStore: IPreferenceStore): IFormattingPreferences = {
    AllPreferences.preferences.foldLeft(FormattingPreferences()) { (preferences, pref) =>
      pref match {
        case pd: BooleanPreferenceDescriptor => preferences.setPreference(pd, preferenceStore(pd))
        case pd: IntegerPreferenceDescriptor => preferences.setPreference(pd, preferenceStore(pd))
        case pd: IntentPreferenceDescriptor =>
          val value = IntentPreference.parseValue(preferenceStore(pd))
          preferences.setPreference(
            pd, value.getOrElse(pd.defaultValue)
          )
      }
    }
  }

  def getPreferences(project: IProject): IFormattingPreferences = getPreferences(getPreferenceStore(project))

  def getPreferences(project: IJavaProject): IFormattingPreferences = getPreferences(project.getProject)

  private def getPreferenceStore(project: IProject): IPreferenceStore = {
    val workspaceStore = IScalaPlugin().getPreferenceStore
    val projectStore = new PropertyStore(new ProjectScope(project), SdtConstants.PluginId)
    val useProjectSettings = projectStore.getBoolean(FormatterPreferences.USE_PROJECT_SPECIFIC_SETTINGS_KEY)
    val prefStore = if (useProjectSettings) projectStore else workspaceStore
    prefStore
  }

  val PREFIX = "formatter."

  val USE_PROJECT_SPECIFIC_SETTINGS_KEY = PREFIX + "useProjectSpecificSettings"

}
