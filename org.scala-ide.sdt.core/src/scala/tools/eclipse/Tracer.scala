package scala.tools.eclipse

/**
 * @author david.bernard
 *
 */
object Tracer {

  /** t0 init time */
  private val t0 = System.currentTimeMillis  

  /**  
   * A very primitive logger to replace call of classic println. 
   */
  def println(s : String) = Console.println("ScalaPlugin--TRACE--" + (System.currentTimeMillis - t0) + "--" + Thread.currentThread.getName + "--:" + s)
  
  /**
   * A very primitive StopWatch (no stats,....)
   */
  def timeOf[T](label : String)(f : => T) : T = {
    val start = System.currentTimeMillis
    println(label + " -BEGIN-> ...")
    try {
      f
    } finally {
      val end = System.currentTimeMillis
      println(label + " -END-> " + (end - start) + "ms")
    }
  }
}