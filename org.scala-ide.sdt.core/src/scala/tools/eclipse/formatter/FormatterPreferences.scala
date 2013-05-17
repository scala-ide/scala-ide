package scala.tools.eclipse.formatter

import org.eclipse.jdt.core.IJavaProject
import org.eclipse.core.resources.IProject
import scalariform.formatter.preferences.IntegerPreferenceDescriptor
import scalariform.formatter.preferences.BooleanPreferenceDescriptor
import scalariform.formatter.preferences.FormattingPreferences
import scalariform.formatter.preferences.AllPreferences
import scala.tools.eclipse.ScalaPlugin
import scalariform.formatter.preferences.IFormattingPreferences
import org.eclipse.jface.preference.IPreferenceStore
import scalariform.formatter.preferences.PreferenceDescriptor
import scala.tools.eclipse.properties.PropertyStore

object FormatterPreferences {

  implicit def preference2PimpedPreference(preference: PreferenceDescriptor[_]) = new PimpedFormatterPreference(preference)

  class PimpedFormatterPreference(preference: PreferenceDescriptor[_]) {

    def eclipseKey = PREFIX  + preference.key

    def oldEclipseKey = OLD_PREFIX + preference.key
  }

  implicit def prefStore2PimpedPrefStore(preferenceStore: IPreferenceStore) = new PimpedPreferenceStore(preferenceStore)

  class PimpedPreferenceStore(preferenceStore: IPreferenceStore) {

    def apply(pref: PreferenceDescriptor[Boolean]) = preferenceStore.getBoolean(pref.eclipseKey)

    def apply(pref: PreferenceDescriptor[Int]) = preferenceStore.getInt(pref.eclipseKey)

    def update(pref: PreferenceDescriptor[Boolean], value: Boolean) { preferenceStore.setValue(pref.eclipseKey, value) }

    def update(pref: PreferenceDescriptor[Int], value: Int) { preferenceStore.setValue(pref.eclipseKey, value) }

    def importPreferences(preferences: IFormattingPreferences) {
      for (preference <- AllPreferences.preferences)
        preference match {
          case bpd: BooleanPreferenceDescriptor =>
            preferenceStore.setValue(preference.eclipseKey, preferences(bpd))
          case ipd: IntegerPreferenceDescriptor =>
            preferenceStore.setValue(preference.eclipseKey, preferences(ipd))
        }
    }

  }

  def getPreferences: IFormattingPreferences = getPreferences(ScalaPlugin.prefStore)

  def getPreferences(preferenceStore: IPreferenceStore): IFormattingPreferences =
    AllPreferences.preferences.foldLeft(FormattingPreferences()) { (preferences, pref) =>
      pref match {
        case pd: BooleanPreferenceDescriptor => preferences.setPreference(pd, preferenceStore(pd))
        case pd: IntegerPreferenceDescriptor => preferences.setPreference(pd, preferenceStore(pd))
      }
    }

  def getPreferences(project: IProject): IFormattingPreferences = getPreferences(getPreferenceStore(project))

  def getPreferences(project: IJavaProject): IFormattingPreferences = getPreferences(project.getProject)

  private def getPreferenceStore(project: IProject): IPreferenceStore = {
    val workspaceStore = ScalaPlugin.prefStore
    val projectStore = new PropertyStore(project, workspaceStore, ScalaPlugin.plugin.pluginId)
    val useProjectSettings = projectStore.getBoolean(FormatterPreferences.USE_PROJECT_SPECIFIC_SETTINGS_KEY)
    val prefStore = if (useProjectSettings) projectStore else ScalaPlugin.prefStore
    prefStore
  }

  val OLD_PREFIX = "scala.tools.eclipse.formatter."

  val PREFIX = "formatter."

  val USE_PROJECT_SPECIFIC_SETTINGS_KEY = PREFIX + "useProjectSpecificSettings"

}