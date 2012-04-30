package scala.tools.eclipse.util

import scala.tools.eclipse.logging.HasLogger

object Utils extends HasLogger {

  /** Return the time in ms required to evaluate `f()`. */
  def time(f: => Any): Long = {
    val start = System.currentTimeMillis()
    f
    System.currentTimeMillis() - start
  }

  /** Evaluate 'f' and return its value and the time required to compute it. */
  def timed[A](f: => A): (A, Long) = {
    val start = System.currentTimeMillis()
    val res = f
    (res, System.currentTimeMillis() - start)
  }

  /** Evaluated `op' and log the time in ms it took to execute it.
   */
  def debugTimed[A](name: String)(op: => A): A = {
    val start = System.currentTimeMillis
    val res = op
    val end = System.currentTimeMillis

    logger.debug("%s: \t %,3d ms".format(name, end - start))
    res
  }

  /** Try executing the passed `action` and log any exception occurring. */
  def tryExecute[T](action: => T, msgIfError: => Option[String] = None): Option[T] = {
    try Some(action)
    catch {
      case t =>
        msgIfError match {
          case Some(errMsg) => eclipseLog.error(errMsg, t)
          case None         => eclipseLog.error(t)
        }
        None
    }
  }

  class WithAsInstanceOfOpt(obj: AnyRef) {
    import scala.reflect.Manifest // this is needed for 2.8 compatibility
    def asInstanceOfOpt[B](implicit m: Manifest[B]): Option[B] =
      if (Manifest.singleType(obj) <:< m)
        Some(obj.asInstanceOf[B])
      else
        None
  }

  implicit def any2optionable(obj: AnyRef): WithAsInstanceOfOpt = new WithAsInstanceOfOpt(obj)

}
