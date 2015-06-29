package org.scalaide.debug.internal.launching

import org.scalaide.ew.launcher.EquinoxWeavingJUnitLaunchConfigurationDelegate

/**
 * Launch configuration delegate starting Scala applications with the Scala debugger.
 */
class ScalaEclipseJUnitLaunchConfigurationDelegate extends EquinoxWeavingJUnitLaunchConfigurationDelegate
  with ScalaDebuggerForLaunchDelegate
