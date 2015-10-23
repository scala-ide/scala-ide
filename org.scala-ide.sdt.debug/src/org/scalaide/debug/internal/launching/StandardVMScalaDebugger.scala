package org.scalaide.debug.internal.launching

import org.eclipse.debug.core.ILaunch
import org.eclipse.debug.core.model.IDebugTarget
import org.eclipse.debug.core.model.IProcess
import org.eclipse.jdt.internal.launching.StandardVMDebugger
import org.eclipse.jdt.launching.IVMInstall
import org.eclipse.jdt.launching.VMRunnerConfiguration
import org.scalaide.debug.internal.model.ScalaDebugTarget

import com.sun.jdi.VirtualMachine

/**
 * Launcher for debug Scala applications using the Scala debugger.
 * Extends the Java debugger launcher, but use the Scala debug model instead of the Java one.
 */
class StandardVMScalaDebugger(vm: IVMInstall) extends StandardVMDebugger(vm) {

  override def createDebugTarget(runnerConfiguration: VMRunnerConfiguration, launch: ILaunch, unusedPort: Int, process: IProcess, virtualMachine: VirtualMachine): IDebugTarget = {
    ScalaDebugTarget(virtualMachine, launch, process,
      allowDisconnect = false, allowTerminate = true,
      classPath = Some(runnerConfiguration.getClassPath))
  }

}
