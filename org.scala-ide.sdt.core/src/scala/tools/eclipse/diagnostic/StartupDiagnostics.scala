package scala.tools.eclipse

package diagnostic

import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.jface.dialogs.IDialogConstants
import org.eclipse.swt.widgets.Display

object StartupDiagnostics {
  import ScalaPlugin.plugin
  
  val INSTALLED_VERSION_KEY = plugin.pluginId + ".diagnostic.currentPluginVersion" 
  val ASK_DIAGNOSTICS = plugin.pluginId + ".diagnostic.askOnUpgrade"
  
  def run {   
    val prefStore = plugin.getPreferenceStore
    val previousVersion = prefStore.getString(INSTALLED_VERSION_KEY)
    val currentVersion = plugin.getBundle.getVersion.toString
    prefStore.setDefault(ASK_DIAGNOSTICS, true)
    val askDiagnostics = prefStore.getBoolean(ASK_DIAGNOSTICS)
    
    println("startup diagnostics: previous version = " + previousVersion)
    println("startup diagnostics: CURRENT version = " + currentVersion)
 
    if (previousVersion != currentVersion) {
      prefStore.setValue(INSTALLED_VERSION_KEY, currentVersion)
      
      if (askDiagnostics) {
        Display.getDefault asyncExec new Runnable { 
          def run() {
            val labels = Array(IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL, "Never")
            val dialog = 
              new MessageDialog(ScalaPlugin.getShell, "Run Scala Setup Diagnostics?", 
                null, "Upgrade of Scala plugin detected. Run setup diagnostics?", MessageDialog.QUESTION, labels, 0)
            dialog.open match {
              case 0 => // user pressed Yes
                new DiagnosticDialog(ScalaPlugin.getShell).open
              case 2 => // user pressed Never
                plugin.getPreferenceStore.setValue(ASK_DIAGNOSTICS, false)
              case _ => // user pressed close button (-1) or No (1)
            }
          }
        }
      }
    }
  }
}