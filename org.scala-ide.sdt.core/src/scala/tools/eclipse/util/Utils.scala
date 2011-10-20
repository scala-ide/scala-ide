package scala.tools.eclipse.util

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
  
  /** Try executing the passed `action` and log any exception occurring. */
  def tryExecute[T](action: => T)(orElse: => T): T = {
    try action
    catch { 
      case t => 
        logger.error(t)
        orElse
    }
  }
}