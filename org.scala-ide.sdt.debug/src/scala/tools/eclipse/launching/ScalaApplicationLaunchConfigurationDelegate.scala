package scala.tools.eclipse.launching

import org.eclipse.debug.core.ILaunchConfiguration
import org.eclipse.debug.core.model.LaunchConfigurationDelegate
import org.eclipse.debug.core.ILaunch
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.launching.IVMRunner

/**
 * Launch configuration delegate starting Scala applications with the Scala debugger.
 */
class ScalaApplicationLaunchConfigurationDelegate extends ScalaLaunchDelegate {

  override def getVMRunner(configuration: ILaunchConfiguration, mode: String): IVMRunner = {
    val vm = verifyVMInstall(configuration)
    new StandardVMScalaDebugger(vm)
  }

}