package scala.tools.eclipse.formatter

import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.core.runtime.preferences.{ AbstractPreferenceInitializer, DefaultScope }

import scala.tools.eclipse.ScalaPlugin
import scalariform.formatter._
import scalariform.formatter.preferences._

class ScalaFormatterPreferenceInitializer extends AbstractPreferenceInitializer {

  import FormatterPreferences._

  def initializeDefaultPreferences() {
    val preferenceStore = ScalaPlugin.plugin.getPreferenceStore
    for (preference <- AllPreferences.preferences) {
      preference match {
        case pd: BooleanPreferenceDescriptor =>
          preferenceStore.setDefault(preference.eclipseKey, pd.defaultValue)
          preferenceStore.setDefault(preference.oldEclipseKey, pd.defaultValue)
          if (!preferenceStore.isDefault(preference.oldEclipseKey)) {
            preferenceStore(pd) = preferenceStore.getBoolean(preference.oldEclipseKey)
            preferenceStore.setToDefault(preference.oldEclipseKey)
          }
        case pd: IntegerPreferenceDescriptor =>
          preferenceStore.setDefault(preference.eclipseKey, pd.defaultValue)
          preferenceStore.setDefault(preference.oldEclipseKey, pd.defaultValue)
          if (!preferenceStore.isDefault(preference.oldEclipseKey)) {
            preferenceStore(pd) = preferenceStore.getInt(preference.oldEclipseKey)
            preferenceStore.setToDefault(preference.oldEclipseKey)
          }
      }
    }

  }
}
