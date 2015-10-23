package org.scalaide.debug.internal.launching

import java.util.{ Map => JMap }

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.debug.core.ILaunch
import org.eclipse.debug.core.ILaunchConfiguration
import org.eclipse.jdt.internal.launching.JavaRemoteApplicationLaunchConfigurationDelegate
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants
import org.scalaide.core.internal.launching.ClasspathGetterForLaunchDelegate

object ScalaRemoteApplicationLaunchConfigurationDelegate {
  val DebugeeProjectClasspath = "DebugeeProjectClasspath"
  val DebugeeProjectClasspathSeparator = ","
}

class ScalaRemoteApplicationLaunchConfigurationDelegate extends JavaRemoteApplicationLaunchConfigurationDelegate
    with ClasspathGetterForLaunchDelegate {
  import ScalaRemoteApplicationLaunchConfigurationDelegate._

  override def launch(configuration: ILaunchConfiguration, mode: String, launch: ILaunch, monitor: IProgressMonitor): Unit = {
    val argMap = configuration.getAttribute(IJavaLaunchConfigurationConstants.ATTR_CONNECT_MAP, null.asInstanceOf[JMap[String, String]])
    argMap.put(DebugeeProjectClasspath, getClasspath(configuration).mkString(DebugeeProjectClasspathSeparator))
    super.launch(configuration, mode, launch, monitor)
  }
}
