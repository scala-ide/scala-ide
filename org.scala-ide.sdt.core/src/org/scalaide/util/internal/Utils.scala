package org.scalaide.util.internal

import org.scalaide.logging.HasLogger
import scala.reflect.ClassTag

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

    logger.debug(f"$name: \t ${end - start}%,3d ms")
    res
  }

  /** Try executing the passed `action` and log any exception occurring. */
  def tryExecute[T](action: => T, msgIfError: => Option[String] = None): Option[T] = {
    try Some(action)
    catch {
      case t: Throwable =>
        msgIfError match {
          case Some(errMsg) => eclipseLog.error(errMsg, t)
          case None         => eclipseLog.error(t)
        }
        None
    }
  }

  implicit class WithAsInstanceOfOpt(obj: AnyRef) {

    /** Half type-safe cast. It uses erasure semantics (like Java casts). For example:
     *
     *  xs: List[Int]
     *
     *  xs.asInstanceOfOpt[List[Int]] == xs.asInstanceOfOpt[List[Double]] == xs.asInstanceOfOpt[Seq[Int]] == Some(xs)
     *
     *  and
     *
     *  xs.asInstanceOfOpt[String] == xs.asInstanceOfOpt[Set[Int]] == None
     *
     *  @return None if the cast fails or the object is null, Some[B] otherwise
     */
    def asInstanceOfOpt[B: ClassTag]: Option[B] = obj match {
      case b: B => Some(b)
      case _    => None
    }
  }
}
