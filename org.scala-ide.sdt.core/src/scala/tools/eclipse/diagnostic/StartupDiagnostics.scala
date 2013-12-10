package scala.tools.eclipse

package diagnostic

import scala.tools.eclipse.contribution.weaving.jdt.configuration.WeavingStateConfigurer
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.ui.DisplayThread
import org.eclipse.ui.PlatformUI
import org.eclipse.jface.preference.IPreferenceStore

/**
 * The regular IPreferenceStore doesn't allow you to directly know whether
 * a value is set or not so this is a Scala wrapper returning Options.
 */
object PreferenceStoreWrapper {
  implicit class PreferenceStoreOps(val store: IPreferenceStore) extends AnyVal {
    def containsNot(name: String) = !store.contains(name)
    def booleanOpt(name: String) =
      if (store.contains(name)) Some(store.getBoolean(name)) else None
    def stringOpt(name: String) =
      if (store.contains(name)) Some(store.getString(name)) else None
    def intOpt(name: String) =
      if (store.contains(name)) Some(store.getInt(name)) else None
    def doubleOpt(name: String) =
      if (store.contains(name)) Some(store.getDouble(name)) else None
    def floatOpt(name: String) =
      if (store.contains(name)) Some(store.getFloat(name)) else None
    def longOpt(name: String) =
      if (store.contains(name)) Some(store.getLong(name)) else None
    def booleanOrElse(name: String, default: Boolean) =
      if (store.contains(name)) store.getBoolean(name) else default
    def stringOrElse(name: String, default: String) =
      if (store.contains(name)) store.getString(name) else default
    def intOrElse(name: String, default: Int) =
      if (store.contains(name)) store.getInt(name) else default
    def doubleOrElse(name: String, default: Double) =
      if (store.contains(name)) store.getDouble(name) else default
    def floatOrElse(name: String, default: Float) =
      if (store.contains(name)) store.getFloat(name) else default
    def longOrElse(name: String, default: Long) =
      if (store.contains(name)) store.getLong(name) else default
  }
}

import org.eclipse.jface.dialogs.IDialogConstants
import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.ui.PlatformUI

object StartupDiagnostics extends HasLogger {
  import ScalaPlugin.plugin

  private val INSTALLED_VERSION_KEY = plugin.pluginId + ".diagnostic.currentPluginVersion"
  val ASK_DIAGNOSTICS = plugin.pluginId + ".diagnostic.askOnUpgrade"

  private val weavingState = new WeavingStateConfigurer

  def suggestDiagnostics(insufficientHeap: Boolean, firstInstall: Boolean, ask: Boolean): Boolean =
    ask && firstInstall && insufficientHeap

  def suggestDiagnostics(prefStore: IPreferenceStore): Boolean = {
    import PreferenceStoreWrapper._
    val firstInstall = prefStore containsNot INSTALLED_VERSION_KEY
    val ask = prefStore.booleanOrElse(ASK_DIAGNOSTICS, default = true)
    suggestDiagnostics(Diagnostics.insufficientHeap, firstInstall, ask)
  }

  def run: Unit = {
    DisplayThread.asyncExec {
      val prefStore = plugin.getPreferenceStore
      if (suggestDiagnostics(prefStore)) {
        val labels = Array(IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL, "Never")
        val dialog =
          new MessageDialog(ScalaPlugin.getShell, "Run Scala Setup Diagnostics?",
            null, "Upgrade of Scala plugin detected.\n\n" +
              "Run setup diagnostics to ensure correct plugin settings?",
            MessageDialog.QUESTION, labels, 0)
        dialog.open match {
          case 0 => // user pressed Yes
            new DiagnosticDialog(weavingState, ScalaPlugin.getShell).open
          case 2 => // user pressed Never
            prefStore.setValue(ASK_DIAGNOSTICS, false)
          case _ => // user pressed close button (-1) or No (1)
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
      val forceWeavingOn = MessageDialog.openConfirm(ScalaPlugin.getShell, "JDT Weaving is disabled",
        """JDT Weaving is currently disabled. The Scala IDE needs JDT Weaving to be active, or it will not work as expected.

Activate JDT Weaving and restart Eclipse? (Highly Recommended)
""")

      if (forceWeavingOn) {
        weavingState.changeWeavingState(true)
        PlatformUI.getWorkbench.restart
      }
    }
  }
}
