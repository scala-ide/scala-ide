package org.scalaide.logging.ui.preferences

private[logging] object LoggingPreferenceConstants {
  private final val Prefix = "scala.tools.eclipse.logging.ui.properties."
  final val LogLevel = Prefix + "LogLevel"
  final val IsConsoleAppenderEnabled = Prefix + "ConsoleAppenderEnabled"
  final val RedirectStdErrOut = Prefix + "RedirectSdtErrOut"
}
