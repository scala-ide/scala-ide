package scala.tools.eclipse.util

/** 
 * Classes can mix this trait for having access to the `logger`. 
 * Clients are allowed to inject a different logger.
 */
trait HasLogger {
  protected[this] val logger: Logger = {
    val clazz = this.getClass
    DefaultLogger(clazz)
  }
}