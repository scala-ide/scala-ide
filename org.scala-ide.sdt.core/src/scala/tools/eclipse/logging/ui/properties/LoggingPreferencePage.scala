package scala.tools.eclipse.logging.ui.properties

import scala.tools.eclipse.logging.Level
import scala.tools.eclipse.logging.LogManager
import scala.tools.eclipse.ui.OpenExternalFile
import scala.tools.eclipse.ScalaPlugin

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.jface.preference.BooleanFieldEditor
import org.eclipse.jface.preference.ComboFieldEditor
import org.eclipse.jface.preference.FieldEditorPreferencePage
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Link
import org.eclipse.swt.SWT
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage

class LoggingPreferencePage extends FieldEditorPreferencePage with IWorkbenchPreferencePage {

  setPreferenceStore(ScalaPlugin.plugin.getPreferenceStore)

  setDescription("General settings for managing logging information in the plugin.")

  override def createFieldEditors() {
    val sortedLevels = Level.values.toArray.sortBy(_.id)
    val namesAndValues = sortedLevels.map(v => Array(v.toString, v.toString))

    addField(new ComboFieldEditor(LoggingPreferenceConstants.LogLevel, "Log Level", namesAndValues, getFieldEditorParent))
    addField(new BooleanFieldEditor(LoggingPreferenceConstants.IsConsoleAppenderEnabled, "Output log in terminal", getFieldEditorParent))
    addField(new BooleanFieldEditor(LoggingPreferenceConstants.RedirectStdErrOut, "Redirect standard out/err to log file", getFieldEditorParent))
  }

  override def createContents(parent: Composite): Control = {
    val control = super.createContents(parent)

    val link = new Link(parent, SWT.NONE)
    link.setText("Click <a>here</a> to open the log file")
    link.addListener(SWT.Selection, OpenExternalFile(LogManager.logFile))

    control
  }

  def init(workbench: IWorkbench) {}
}

class LoggingPreferencePageInitializer extends AbstractPreferenceInitializer {
  override def initializeDefaultPreferences() {
    val store = ScalaPlugin.plugin.getPreferenceStore
    if(ScalaPlugin.plugin.headlessMode) {
      store.setDefault(LoggingPreferenceConstants.LogLevel, Level.DEBUG.toString)
      store.setDefault(LoggingPreferenceConstants.IsConsoleAppenderEnabled, true)
      store.setDefault(LoggingPreferenceConstants.RedirectStdErrOut, false)
    }
    else {
      store.setDefault(LoggingPreferenceConstants.LogLevel, LogManager.defaultLogLevel.toString)
      store.setDefault(LoggingPreferenceConstants.IsConsoleAppenderEnabled, false)
      store.setDefault(LoggingPreferenceConstants.RedirectStdErrOut, true)
    }
  }
}