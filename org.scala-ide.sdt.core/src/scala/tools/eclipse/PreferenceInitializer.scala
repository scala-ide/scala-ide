package scala.tools.eclipse

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.core.runtime.preferences.DefaultScope
import scala.tools.eclipse.diagnostic.StartupDiagnostics

class PreferenceInitializer extends AbstractPreferenceInitializer {

  def initializeDefaultPreferences(): Unit = {
    val node = new DefaultScope().getNode("org.scala-ide.sdt.core");
    node.putBoolean(StartupDiagnostics.ASK_DIAGNOSTICS, true);
  }

}