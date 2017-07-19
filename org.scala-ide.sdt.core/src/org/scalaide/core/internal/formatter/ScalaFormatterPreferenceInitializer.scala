package org.scalaide.core.internal.formatter

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.scalaide.core.IScalaPlugin
import scalariform.formatter.preferences._

class ScalaFormatterPreferenceInitializer extends AbstractPreferenceInitializer {

  import FormatterPreferences._

  def initializeDefaultPreferences(): Unit = {
    implicit val preferenceStore = IScalaPlugin().getPreferenceStore
    for (preference <- AllPreferences.preferences) {
      preference match {
        case pd: BooleanPreferenceDescriptor => preferenceStore.setDefault(pd.eclipseKey, pd.defaultValue)
        case pd: IntegerPreferenceDescriptor => preferenceStore.setDefault(pd.eclipseKey, pd.defaultValue)
        case pd: IntentPreferenceDescriptor =>
          preferenceStore.setDefault(
            pd.eclipseKey, pd.defaultValue.toString
          )
      }
    }

  }
}
