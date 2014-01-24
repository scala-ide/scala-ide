package scala.tools.eclipse.logging

/**
 * Classes can mix this trait for having access to both the "default" {{{logger}}}
 * and the {{{eclipseLog}}}. The {{{eclipseLog}}} is a handle on the
 * {{{org.eclipse.core.runtime.Plugin.getLog}} instance.
 *
 * Clients can inject different loggers if needed.
 */
trait HasLogger {
  protected[this] lazy val logger: Logger = {
    val clazz = this.getClass
    LogManager.getLogger(clazz)
  }

  protected[this] def eclipseLog: Logger = EclipseLogger
}