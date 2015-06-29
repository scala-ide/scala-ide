package org.scalaide.core.internal.formatter

import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.core.runtime.preferences.DefaultScope
import org.scalaide.core.IScalaPlugin
import scalariform.formatter.preferences._

class ScalaFormatterPreferenceInitializer extends AbstractPreferenceInitializer {

  import FormatterPreferences._

  def initializeDefaultPreferences(): Unit = {
    implicit val preferenceStore = IScalaPlugin().getPreferenceStore
    for (preference <- AllPreferences.preferences) {
      preference match {
        case DoubleIndentClassDeclaration =>
          setDefaultBoolean(DoubleIndentClassDeclaration, overrideValue = Some(true))
        case pd: BooleanPreferenceDescriptor =>
          setDefaultBoolean(pd)
        case pd: IntegerPreferenceDescriptor =>
          setDefaultInt(pd)
      }
    }

  }

  private def setDefaultBoolean(preference: PreferenceDescriptor[Boolean],
    overrideValue: Option[Boolean] = None)(implicit preferenceStore: IPreferenceStore) = {
    val defaultValue = overrideValue.getOrElse(preference.defaultValue)
    preferenceStore.setDefault(preference.eclipseKey, defaultValue)
    preferenceStore.setDefault(preference.oldEclipseKey, defaultValue)
    if (!preferenceStore.isDefault(preference.oldEclipseKey)) {
      preferenceStore(preference) = preferenceStore.getBoolean(preference.oldEclipseKey)
      preferenceStore.setToDefault(preference.oldEclipseKey)
    }
  }

  private def setDefaultInt(preference: PreferenceDescriptor[Int],
    overrideValue: Option[Int] = None)(implicit preferenceStore: IPreferenceStore) = {
    val defaultValue = overrideValue.getOrElse(preference.defaultValue)
    preferenceStore.setDefault(preference.eclipseKey, defaultValue)
    preferenceStore.setDefault(preference.oldEclipseKey, defaultValue)
    if (!preferenceStore.isDefault(preference.oldEclipseKey)) {
      preferenceStore(preference) = preferenceStore.getInt(preference.oldEclipseKey)
      preferenceStore.setToDefault(preference.oldEclipseKey)
    }
  }
}
