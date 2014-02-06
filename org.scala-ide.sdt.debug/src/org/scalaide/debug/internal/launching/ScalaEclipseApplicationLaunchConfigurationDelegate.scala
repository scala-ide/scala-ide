package org.scalaide.debug.internal.launching

import org.eclipse.debug.core.ILaunchConfiguration
import org.eclipse.jdt.launching.IVMRunner
import org.scalaide.ew.launcher.EquinoxWeavingApplicationLaunchConfiguration
import org.eclipse.pde.internal.launching.launcher.VMHelper

/**
 * Launch configuration delegate starting Scala applications with the Scala debugger.
 */
class ScalaEclipseApplicationLaunchConfigurationDelegate extends EquinoxWeavingApplicationLaunchConfiguration {

  override def getVMRunner(configuration: ILaunchConfiguration, mode: String): IVMRunner = {
    val vm = VMHelper.createLauncher(configuration)
    new StandardVMScalaDebugger(vm)
  }

}
