package org.scalaide.debug.internal.launching

import org.eclipse.debug.core.ILaunchConfiguration
import org.eclipse.debug.core.ILaunchManager
import org.eclipse.jdt.launching.IVMRunner
import org.scalaide.core.internal.launching.ScalaLaunchDelegate

/**
 * Launch configuration delegate starting Scala applications with the Scala debugger.
 */
class ScalaApplicationLaunchConfigurationDelegate extends ScalaLaunchDelegate {

  override def getVMRunner(configuration: ILaunchConfiguration, mode: String): IVMRunner = {
    if (ILaunchManager.DEBUG_MODE == mode) {
      val vm = verifyVMInstall(configuration)
      new StandardVMScalaDebugger(vm)
    } else {
      super.getVMRunner(configuration, mode)
    }
  }

}