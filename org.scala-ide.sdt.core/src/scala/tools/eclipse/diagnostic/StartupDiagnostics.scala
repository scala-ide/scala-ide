package scala.tools.eclipse

package diagnostic


import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.jface.dialogs.IDialogConstants
import util.SWTUtils.asyncExec
import scala.tools.eclipse.util.HasLogger

object StartupDiagnostics extends HasLogger {
  import ScalaPlugin.plugin
  
  val INSTALLED_VERSION_KEY = plugin.pluginId + ".diagnostic.currentPluginVersion" 
  val ASK_DIAGNOSTICS = plugin.pluginId + ".diagnostic.askOnUpgrade"
  
  def run {   
    val prefStore = plugin.getPreferenceStore
    val previousVersion = prefStore.getString(INSTALLED_VERSION_KEY)
    val currentVersion = plugin.getBundle.getVersion.toString
    prefStore.setDefault(ASK_DIAGNOSTICS, true)
    val askDiagnostics = prefStore.getBoolean(ASK_DIAGNOSTICS)
    
    logger.info("startup diagnostics: previous version = " + previousVersion)
    logger.info("startup diagnostics: CURRENT version = " + currentVersion)
 
    if (previousVersion != currentVersion) {
      prefStore.setValue(INSTALLED_VERSION_KEY, currentVersion)
      
      if (askDiagnostics) {
        asyncExec { 
            val labels = Array(IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL, "Never")
            val dialog = 
              new MessageDialog(ScalaPlugin.getShell, "Run Scala Setup Diagnostics?", 
                null, "Upgrade of Scala plugin detected.\n\n" +
                "Run setup diagnostics to ensure correct plugin settings?",
                MessageDialog.QUESTION, labels, 0)
            dialog.open match {
              case 0 => // user pressed Yes
                new DiagnosticDialog(ScalaPlugin.getShell).open
              case 2 => // user pressed Never
                plugin.getPreferenceStore.setValue(ASK_DIAGNOSTICS, false)
              case _ => // user pressed close button (-1) or No (1)
            }
            
            ScalaPlugin.plugin.savePluginPreferences // TODO: this method is deprecated, but the solution given in the docs is unclear and is not used by Eclipse itself. -DM
        }
      }
    }
  }
}
