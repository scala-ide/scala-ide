package scala.tools.eclipse.launching

import org.eclipse.debug.core.ILaunchConfiguration
import org.eclipse.jdt.launching.IVMRunner
import org.eclipse.pde.internal.launching.launcher.VMHelper
import org.scalaide.ew.launcher.EquinoxWeavingJUnitLaunchConfigurationDelegate

/**
 * Launch configuration delegate starting Scala applications with the Scala debugger.
 */
class ScalaEclipseJUnitLaunchConfigurationDelegate extends EquinoxWeavingJUnitLaunchConfigurationDelegate {

  override def getVMRunner(configuration: ILaunchConfiguration, mode: String): IVMRunner = {
    val vm = VMHelper.createLauncher(configuration)
    new StandardVMScalaDebugger(vm)
  }

}
