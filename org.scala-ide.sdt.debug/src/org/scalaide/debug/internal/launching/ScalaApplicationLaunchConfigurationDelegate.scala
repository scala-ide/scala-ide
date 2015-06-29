package org.scalaide.debug.internal.launching

import org.scalaide.core.internal.launching.ScalaLaunchDelegate

/**
 * Launch configuration delegate starting Scala applications with the Scala debugger.
 */
class ScalaApplicationLaunchConfigurationDelegate extends ScalaLaunchDelegate
  with ScalaDebuggerForLaunchDelegate
