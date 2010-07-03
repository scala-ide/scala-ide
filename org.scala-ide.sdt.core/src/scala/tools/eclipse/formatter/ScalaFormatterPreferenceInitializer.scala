package scala.tools.eclipse.formatter

import org.eclipse.core.runtime.preferences.{ AbstractPreferenceInitializer, DefaultScope }

import scala.tools.eclipse.ScalaPlugin
import scalariform.formatter._
import scalariform.formatter.preferences._

class ScalaFormatterPreferenceInitializer extends AbstractPreferenceInitializer {

  def initializeDefaultPreferences(): Unit =
    ScalaPlugin.plugin.check {
      val preferenceStore = ScalaPlugin.plugin.getPluginPreferences
      for {
        preference <- AllPreferences.preferences
        val key = FormatterPreferencePage.prefix + preference.key
      } preference.preferenceType match {
        case prefType@BooleanPreference =>
          preferenceStore.setDefault(key, prefType.cast(preference).defaultValue)
        case prefType@IntegerPreference(_, _) =>
          preferenceStore.setDefault(key, prefType.cast(preference).defaultValue)
      }

    }
}
