package org.scalaide.debug.internal.launching

import org.eclipse.debug.core.ILaunchConfiguration
import org.eclipse.jdt.launching.IVMRunner
import org.scalaide.ew.launcher.EquinoxWeavingApplicationLaunchConfiguration
import org.eclipse.jdt.launching.JavaRuntime

/**
 * Launch configuration delegate starting Scala applications with the Scala debugger.
 */
class ScalaEclipseApplicationLaunchConfigurationDelegate extends EquinoxWeavingApplicationLaunchConfiguration {

  override def getVMRunner(configuration: ILaunchConfiguration, mode: String): IVMRunner = {
    val vm = JavaRuntime.computeVMInstall(configuration)
    new StandardVMScalaDebugger(vm)
  }

}
