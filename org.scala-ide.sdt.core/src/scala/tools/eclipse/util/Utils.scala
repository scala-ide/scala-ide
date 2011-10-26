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

  class WithAsInstanceOfOpt(obj: AnyRef) {
    def asInstanceOfOpt[B](implicit m: Manifest[B]): Option[B] =
      if (ManifestUtil.singleType(obj) <:< m)
        Some(obj.asInstanceOf[B])
      else
        None
  }
  
  @deprecated("Remove module when 2.8 support is dropped")
  object ManifestUtil {
    private class SingletonTypeManifest[T <: AnyRef](value: AnyRef) extends Manifest[T] {
      lazy val erasure = value.getClass
      override lazy val toString = value.toString + ".type"
    }

    /** Manifest for the singleton type `value.type'. */
    @deprecated("Use Manifest.singleType when support for 2.8 is dropped")
    def singleType[T <: AnyRef](value: AnyRef): Manifest[T] =
      new SingletonTypeManifest[T](value)
  }

  implicit def any2optionable(obj: AnyRef): WithAsInstanceOfOpt = new WithAsInstanceOfOpt(obj)
}