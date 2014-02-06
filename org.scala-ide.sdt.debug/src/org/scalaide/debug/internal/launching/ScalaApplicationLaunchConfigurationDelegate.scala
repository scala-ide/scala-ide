package org.scalaide.debug.internal.launching

import org.eclipse.debug.core.ILaunchConfiguration
import org.eclipse.jdt.launching.IVMRunner
import org.scalaide.core.internal.launching.ScalaLaunchDelegate

/**
 * Launch configuration delegate starting Scala applications with the Scala debugger.
 */
class ScalaApplicationLaunchConfigurationDelegate extends ScalaLaunchDelegate {

  override def getVMRunner(configuration: ILaunchConfiguration, mode: String): IVMRunner = {
    val vm = verifyVMInstall(configuration)
    new StandardVMScalaDebugger(vm)
  }

}