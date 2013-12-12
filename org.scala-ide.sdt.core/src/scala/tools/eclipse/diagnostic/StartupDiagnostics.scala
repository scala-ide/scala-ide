package scala.tools.eclipse

package diagnostic

import scala.tools.eclipse.contribution.weaving.jdt.configuration.WeavingStateConfigurer
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.ui.DisplayThread
import org.eclipse.ui.PlatformUI
import org.eclipse.jface.preference.IPreferenceStore


object MessageDialog {
  import org.eclipse.jface.dialogs.{ MessageDialog => JFMessageDialog }
  import org.eclipse.swt.widgets.Shell
  def apply(heading: String, message: String, labels: (Int, String)*) =
    new JFMessageDialog(ScalaPlugin.getShell, heading, null, message, JFMessageDialog.QUESTION, labels.map(_._2).toArray, 0).open()
  def confirm(heading: String, message: String) =
    JFMessageDialog.openConfirm(ScalaPlugin.getShell, heading, message)
  def question(heading: String, message: String) =
    JFMessageDialog.openQuestion(ScalaPlugin.getShell, heading, message)
  val CLOSE_ACTION = -1
}

object StartupDiagnostics extends HasLogger {
  import ScalaPlugin.plugin

  private val INSTALLED_VERSION_KEY = plugin.pluginId + ".diagnostic.currentPluginVersion"
  val ASK_DIAGNOSTICS = plugin.pluginId + ".diagnostic.askOnUpgrade"

  private val weavingState = new WeavingStateConfigurer

  def suggestDiagnostics(insufficientHeap: Boolean, firstInstall: Boolean, ask: Boolean): Boolean =
    ask && firstInstall && insufficientHeap

  def suggestDiagnostics(prefStore: IPreferenceStore): Boolean = {
    val firstInstall = (prefStore getString INSTALLED_VERSION_KEY) == ""
    val ask = prefStore getBoolean ASK_DIAGNOSTICS
    suggestDiagnostics(Diagnostics.insufficientHeap, firstInstall, ask)
  }

  def run() {
    val YES_ACTION = 0
    val NO_ACTION = 1
    val NEVER_ACTION = 2
    import MessageDialog.CLOSE_ACTION

    val prefStore = plugin.getPreferenceStore
    DisplayThread.asyncExec {
      if (suggestDiagnostics(prefStore)) {
        import org.eclipse.jface.dialogs.IDialogConstants._
        MessageDialog(
          "Run Scala Setup Diagnostics?",
          """|We detected that some of your settings are not adequate for the Scala IDE plugin.
             |
             |Run setup diagnostics to ensure correct plugin settings?""".stripMargin,
          YES_ACTION -> YES_LABEL, NO_ACTION -> NO_LABEL, NEVER_ACTION -> "Never") match {
            case YES_ACTION =>
              new DiagnosticDialog(weavingState, ScalaPlugin.getShell).open
            case NEVER_ACTION =>
              prefStore.setValue(ASK_DIAGNOSTICS, false)
            case NO_ACTION | CLOSE_ACTION =>
          }
        val currentVersion = plugin.getBundle.getVersion.toString
        prefStore.setValue(INSTALLED_VERSION_KEY, currentVersion)
        ScalaPlugin.plugin.savePluginPreferences // TODO: this method is deprecated, but the solution given in the docs is unclear and is not used by Eclipse itself. -DM
      }
      ensureWeavingIsEnabled()
    }
  }

  private def ensureWeavingIsEnabled(): Unit = {
    if (!weavingState.isWeaving) {
      val forceWeavingOn = MessageDialog.confirm(
        "JDT Weaving is disabled",
        """|JDT Weaving is currently disabled. The Scala IDE needs JDT Weaving to be active, or it will not work as expected.
           |Activate JDT Weaving and restart Eclipse? (Highly Recommended)""".stripMargin)

      if (forceWeavingOn) {
        weavingState.changeWeavingState(true)
        PlatformUI.getWorkbench.restart
      }
    }
  }
}
