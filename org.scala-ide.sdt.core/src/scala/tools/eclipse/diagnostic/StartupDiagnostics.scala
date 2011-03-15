package scala.tools.eclipse

package diagnostic

import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.swt.widgets.Display

object StartupDiagnostics {
  import ScalaPlugin.plugin
  
  val INSTALLED_VERSION_KEY = plugin.pluginId + ".diagnostic.currentPluginVersion" 
  
  def run {   
    val prefStore = plugin.getPreferenceStore
    val previousVersion = prefStore.getString(INSTALLED_VERSION_KEY)
    val currentVersion = plugin.getBundle.getVersion.toString
    
    println("startup diagnostics: previous version = " + previousVersion)
    println("startup diagnostics: CURRENT version = " + currentVersion)
 
    if (previousVersion != currentVersion) {
      prefStore.setValue(INSTALLED_VERSION_KEY, currentVersion)
      
      Display.getDefault asyncExec new Runnable { 
        def run() {
          val result = MessageDialog.openQuestion(ScalaPlugin.getShell, "Run Scala Setup Diagnostics?",
            "Upgrade of Scala plugin detected. Run setup diagnostics?")
  
          if (result) {
            new DiagnosticDialog(ScalaPlugin.getShell).open
          }
        }
      }
    }
  }
}