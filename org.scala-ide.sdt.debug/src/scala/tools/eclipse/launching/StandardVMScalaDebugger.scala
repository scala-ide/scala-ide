package scala.tools.eclipse.launching

import org.eclipse.jdt.internal.launching.StandardVMDebugger
import org.eclipse.jdt.launching.IVMInstall
import org.eclipse.jdt.launching.VMRunnerConfiguration
import org.eclipse.debug.core.ILaunch
import com.sun.jdi.VirtualMachine
import org.eclipse.debug.core.model.IProcess
import org.eclipse.debug.core.model.IDebugTarget
import scala.tools.eclipse.debug.model.ScalaDebugTarget

/**
 * Launcher for debug Scala applications using the Scala debugger.
 * Extends the Java debugger launcher, but use the Scala debug model instead of the Java one.
 */
class StandardVMScalaDebugger(vm: IVMInstall) extends StandardVMDebugger(vm) {
  
  override def createDebugTarget(configuration: VMRunnerConfiguration, launch: ILaunch, port: Int, process: IProcess, virtualMachine: VirtualMachine): IDebugTarget = {
    ScalaDebugTarget(virtualMachine, launch, process)
  }

}