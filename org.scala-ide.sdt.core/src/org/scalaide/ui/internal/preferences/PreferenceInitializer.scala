package org.scalaide.ui.internal.preferences

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.core.runtime.preferences.DefaultScope
import org.scalaide.ui.internal.diagnostic.StartupDiagnostics

class PreferenceInitializer extends AbstractPreferenceInitializer {

  def initializeDefaultPreferences(): Unit = {
    val node = DefaultScope.INSTANCE.getNode("org.scala-ide.sdt.core");
    node.putBoolean(StartupDiagnostics.ASK_DIAGNOSTICS, true);
  }

}
